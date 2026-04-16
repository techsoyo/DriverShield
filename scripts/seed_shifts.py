"""
DriverShield — Seed de turnos via Python sqlite3
Inserta 10 sesiones NIGHT (01/04/2026 – 10/04/2026)
Turno: 18:00 CEST -> 06:00 CEST día siguiente | durationMs = 8.5h (sin 3.5h descanso)
"""

import sqlite3
import subprocess
import sys
import os

# ── Configuración ──────────────────────────────────────────────────────────────
PACKAGE  = "com.drivershield"
DB_NAME  = "drivershield_db"
REMOTE   = f"/data/data/{PACKAGE}/databases/"
WIN_TMP  = r"C:\Users\kikko\AppData\Local\Temp\ds_db"
DB_LOCAL = os.path.join(WIN_TMP, DB_NAME)

# ── Timestamps (UTC epoch ms) ──────────────────────────────────────────────────
# CEST = UTC+2  ->  18:00 local = 16:00 UTC  |  06:00 local siguiente = 04:00 UTC siguiente
# 2026-04-01 00:00:00 UTC = 1_775_001_600_000 ms
BASE_MS   = 1_775_001_600_000
DAY_MS    = 86_400_000
START_OFF = 16 * 3_600_000          # 16:00 UTC = 18:00 CEST
END_OFF   = DAY_MS + 4 * 3_600_000  # día siguiente 04:00 UTC = 06:00 CEST
DURATION  = 30_600_000              # 8.5h netas (12h total – 3.5h descanso)

SHIFTS = [
    ("01/04/2026 Miércoles", 14, BASE_MS + 0*DAY_MS + START_OFF, BASE_MS + 0*DAY_MS + END_OFF),
    ("02/04/2026 Jueves",    14, BASE_MS + 1*DAY_MS + START_OFF, BASE_MS + 1*DAY_MS + END_OFF),
    ("03/04/2026 Viernes",   14, BASE_MS + 2*DAY_MS + START_OFF, BASE_MS + 2*DAY_MS + END_OFF),
    ("04/04/2026 Sábado",    14, BASE_MS + 3*DAY_MS + START_OFF, BASE_MS + 3*DAY_MS + END_OFF),
    ("05/04/2026 Domingo",   14, BASE_MS + 4*DAY_MS + START_OFF, BASE_MS + 4*DAY_MS + END_OFF),
    ("06/04/2026 Lunes",     15, BASE_MS + 5*DAY_MS + START_OFF, BASE_MS + 5*DAY_MS + END_OFF),
    ("07/04/2026 Martes",    15, BASE_MS + 6*DAY_MS + START_OFF, BASE_MS + 6*DAY_MS + END_OFF),
    ("08/04/2026 Miércoles", 15, BASE_MS + 7*DAY_MS + START_OFF, BASE_MS + 7*DAY_MS + END_OFF),
    ("09/04/2026 Jueves",    15, BASE_MS + 8*DAY_MS + START_OFF, BASE_MS + 8*DAY_MS + END_OFF),
    ("10/04/2026 Viernes",   15, BASE_MS + 9*DAY_MS + START_OFF, BASE_MS + 9*DAY_MS + END_OFF),
]

# ── Helpers ADB ───────────────────────────────────────────────────────────────
def adb_text(*args):
    """Comando ADB que devuelve texto."""
    r = subprocess.run(["adb"] + list(args), capture_output=True,
                       encoding="utf-8", errors="replace")
    return r.returncode, r.stdout.strip(), r.stderr.strip()

def adb_binary(*args):
    """Comando ADB que devuelve bytes (para archivos binarios)."""
    r = subprocess.run(["adb"] + list(args), capture_output=True)
    return r.returncode, r.stdout, r.stderr

# ── 1. Parar la app ───────────────────────────────────────────────────────────
print("[1/5] Cerrando app...")
adb_text("shell", "am", "force-stop", PACKAGE)
print("  OK")

# ── 2. Pull base de datos ─────────────────────────────────────────────────────
print("[2/5] Descargando BD del dispositivo...")
os.makedirs(WIN_TMP, exist_ok=True)

for f in [DB_NAME, DB_NAME + "-shm", DB_NAME + "-wal"]:
    remote_path = REMOTE + f
    local_path  = os.path.join(WIN_TMP, f)
    rc, data, err = adb_binary("exec-out", f"run-as {PACKAGE} cat {remote_path}")
    if len(data) == 0:
        print(f"  {f}: vacío o no existe (normal para WAL/SHM)")
        with open(local_path, 'wb') as fp:
            pass  # archivo vacío
    else:
        with open(local_path, 'wb') as fp:
            fp.write(data)
        print(f"  {f}: {len(data):,} bytes")

# ── 3. Insertar datos con Python sqlite3 ──────────────────────────────────────
print("[3/5] Insertando turnos en BD local...")
conn = sqlite3.connect(DB_LOCAL)
conn.execute("PRAGMA foreign_keys = OFF")

cur = conn.cursor()
cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
tables = {r[0] for r in cur.fetchall()}
if "shift_sessions" not in tables:
    print("  ERROR: La BD descargada no tiene las tablas esperadas.")
    print("  Tablas encontradas:", tables)
    conn.close()
    sys.exit(1)

session_ids = []
for label, iso_week, start_ms, end_ms in SHIFTS:
    cur.execute("""
        INSERT INTO shift_sessions
            (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
        VALUES (?, ?, 'NIGHT', ?, ?, 2026, '', 0)
    """, (start_ms, end_ms, DURATION, iso_week))
    sid = cur.lastrowid
    session_ids.append(sid)

    cur.execute("""
        INSERT INTO shift_events
            (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
        VALUES (?, 'START_WORK', ?, 0, 1)
    """, (sid, start_ms))
    cur.execute("""
        INSERT INTO shift_events
            (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
        VALUES (?, 'END_SHIFT', ?, 0, 1)
    """, (sid, end_ms))

    print(f"  ID={sid}  {label}")

# Agregados semanales
for iso_week, last_end in [(14, SHIFTS[4][3]), (15, SHIFTS[9][3])]:
    cur.execute("""
        INSERT OR REPLACE INTO weekly_aggregates
            (isoYear, isoWeekNumber, totalWorkMs, totalRestMs, sessionCount, lastUpdated)
        VALUES (2026, ?, 153000000, 0, 5, ?)
    """, (iso_week, last_end))

conn.commit()
# Checkpoint WAL -> vuelca todo al archivo principal y lo trunca
conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
conn.close()
print("  Commit + WAL checkpoint OK")

# ── 4. Verificar localmente ───────────────────────────────────────────────────
print("[4/5] Verificando datos locales...")
conn = sqlite3.connect(DB_LOCAL)
cur = conn.cursor()
cur.execute("SELECT COUNT(*) FROM shift_sessions"); n_s = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM shift_events");  n_e = cur.fetchone()[0]
cur.execute("SELECT isoWeekNumber, sessionCount, totalWorkMs FROM weekly_aggregates ORDER BY isoWeekNumber")
agg = cur.fetchall()
conn.close()
print(f"  shift_sessions : {n_s}")
print(f"  shift_events   : {n_e}")
for row in agg:
    print(f"  Semana {row[0]}: {row[1]} sesiones, {row[2]/3600000:.1f}h trabajo")

# ── 5. Push al dispositivo ────────────────────────────────────────────────────
print("[5/5] Subiendo BD al dispositivo...")

tmp_device = f"/data/local/tmp/{DB_NAME}"
remote = REMOTE + DB_NAME

# Subir a /data/local/tmp/ (accesible al usuario shell)
rc, out, err = adb_text("push", DB_LOCAL, tmp_device)
if rc != 0:
    print(f"  ERROR en push: {err}")
    sys.exit(1)
print(f"  Push -> {tmp_device}")

# Dar permisos de lectura universal para que run-as pueda leerlo
adb_text("shell", "chmod", "644", tmp_device)

# Copiar desde /data/local/tmp/ al directorio privado de la app via run-as
# run-as usa /data/data/PACKAGE como cwd, pero necesitamos ruta absoluta destino
rc, out, err = adb_text("shell", "run-as", PACKAGE,
                        "cp", tmp_device, f"/data/data/{PACKAGE}/databases/{DB_NAME}")
if rc != 0:
    print(f"  ERROR copiando a BD: {err}")
    sys.exit(1)
print(f"  Copiado -> {remote}")

# Limpiar temporal
adb_text("shell", "rm", tmp_device)

# Eliminar WAL y SHM residuales (la app los recrea limpios)
for f in [DB_NAME + "-wal", DB_NAME + "-shm"]:
    adb_text("shell", "run-as", PACKAGE, "sh", "-c", f"rm -f {REMOTE}{f}")
print("  WAL/SHM remotos eliminados.")

print()
print("=" * 52)
print(" SEED COMPLETADO")
print("=" * 52)
print(f"  {n_s} sesiones NIGHT  |  {n_e} eventos de turno")
print("  Abre la app -> Historial")
print("  Rango a introducir: 01/04/2026 – 10/04/2026")
print("=" * 52)
