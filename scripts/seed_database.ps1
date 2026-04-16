# DriverShield — Seed de base de datos via ADB
# Uso: .\scripts\seed_database.ps1
# Requiere: ADB en PATH + emulador corriendo o dispositivo debug conectado

$ErrorActionPreference = "Stop"

$PACKAGE   = "com.drivershield"
$DB_PATH   = "/data/data/$PACKAGE/databases/drivershield_db"
$SQL_LOCAL = "$PSScriptRoot\seed_shifts.sql"
$SQL_TMP   = "/data/local/tmp/ds_seed.sql"

# ── 1. Verificar ADB disponible ───────────────────────────────────────────────
Write-Host "[1/6] Verificando ADB..." -ForegroundColor Cyan
try {
    $adbVersion = & adb version 2>&1
} catch {
    Write-Error "ADB no encontrado en PATH. Instala Android SDK Platform-Tools."
    exit 1
}

# ── 2. Verificar dispositivo conectado ────────────────────────────────────────
Write-Host "[2/6] Buscando dispositivo..." -ForegroundColor Cyan
$devices = & adb devices
$connectedDevices = @(($devices -split "`n") | Where-Object { $_ -match "\tdevice$" })
if ($connectedDevices.Count -eq 0) {
    Write-Error "No hay ningun dispositivo/emulador conectado. Inicia el emulador primero."
    exit 1
}
$firstDevice = [string]$connectedDevices[0]
Write-Host "      Dispositivo: $($firstDevice.Split("`t")[0])" -ForegroundColor Green

# ── 3. Forzar cierre de la app (libera el lock de la BD) ─────────────────────
Write-Host "[3/6] Cerrando app..." -ForegroundColor Cyan
& adb shell am force-stop $PACKAGE
Start-Sleep -Milliseconds 800

# ── 4. Elevar permisos si es emulador ────────────────────────────────────────
Write-Host "[4/6] Intentando adb root..." -ForegroundColor Cyan
$rootResult = & adb root 2>&1
if ($rootResult -match "cannot run as root") {
    Write-Host "      Root no disponible (dispositivo real). Usando run-as." -ForegroundColor Yellow
    $useRunAs = $true
} else {
    Write-Host "      Root OK." -ForegroundColor Green
    $useRunAs = $false
    Start-Sleep -Milliseconds 500
    & adb remount 2>$null
}

# ── 5. Enviar SQL y ejecutar ──────────────────────────────────────────────────
Write-Host "[5/6] Cargando datos en la BD..." -ForegroundColor Cyan

# Push del archivo SQL a almacenamiento temporal del dispositivo
& adb push $SQL_LOCAL $SQL_TMP | Out-Null

# sqlite3 puede estar en /system/bin o en PATH; usar ruta completa para run-as
$SQLITE3 = "/system/bin/sqlite3"

if ($useRunAs) {
    # Copiar SQL al espacio privado de la app (run-as solo accede a /data/data/pkg/)
    & adb shell run-as $PACKAGE sh -c "cp $SQL_TMP /data/data/$PACKAGE/ds_seed.sql"
    $result = & adb shell run-as $PACKAGE $SQLITE3 $DB_PATH ".read /data/data/$PACKAGE/ds_seed.sql" 2>&1
    & adb shell run-as $PACKAGE sh -c "rm /data/data/$PACKAGE/ds_seed.sql" 2>$null
} else {
    # Ejecución directa con root
    $result = & adb shell $SQLITE3 $DB_PATH ".read $SQL_TMP" 2>&1
}

# Limpiar archivo temporal
& adb shell sh -c "rm $SQL_TMP" 2>$null

if ($LASTEXITCODE -ne 0 -and $result -match "Error|error|no such") {
    Write-Error "Error ejecutando SQL: $result"
    exit 1
}

# ── 6. Verificar registros insertados ─────────────────────────────────────────
Write-Host "[6/6] Verificando insercion..." -ForegroundColor Cyan

if ($useRunAs) {
    $countSessions = & adb shell run-as $PACKAGE $SQLITE3 $DB_PATH "SELECT COUNT(*) FROM shift_sessions;"
    $countEvents   = & adb shell run-as $PACKAGE $SQLITE3 $DB_PATH "SELECT COUNT(*) FROM shift_events;"
    $weeks         = & adb shell run-as $PACKAGE $SQLITE3 $DB_PATH "SELECT isoWeekNumber, sessionCount, totalWorkMs FROM weekly_aggregates ORDER BY isoWeekNumber;"
} else {
    $countSessions = & adb shell $SQLITE3 $DB_PATH "SELECT COUNT(*) FROM shift_sessions;"
    $countEvents   = & adb shell $SQLITE3 $DB_PATH "SELECT COUNT(*) FROM shift_events;"
    $weeks         = & adb shell $SQLITE3 $DB_PATH "SELECT isoWeekNumber, sessionCount, totalWorkMs FROM weekly_aggregates ORDER BY isoWeekNumber;"
}

Write-Host ""
Write-Host "════════════════════════════════════" -ForegroundColor Green
Write-Host " Seed completado correctamente" -ForegroundColor Green
Write-Host "════════════════════════════════════" -ForegroundColor Green
Write-Host "  shift_sessions : $countSessions registros"
Write-Host "  shift_events   : $countEvents registros"
Write-Host "  weekly_aggregates:"
$weeks | ForEach-Object { Write-Host "    $_" }
Write-Host ""
Write-Host " Abre la app → Historial → selecciona rango 01/04/2026 - 10/04/2026" -ForegroundColor Cyan
