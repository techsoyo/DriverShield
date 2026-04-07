"""
generar_auditoria.py
Genera auditoria_codigos.txt con la estructura completa del proyecto
y el contenido de todos los archivos de código relevantes.
Uso: python generar_auditoria.py
"""

import os
from pathlib import Path
from datetime import datetime

# ── Configuración ─────────────────────────────────────────────────────────────

ROOT = Path(__file__).parent.resolve()
OUTPUT = ROOT / "auditoria_codigos.txt"

# Extensiones de archivo que se incluyen en el volcado completo
INCLUDE_EXTENSIONS = {
    ".kt", ".kts", ".xml", ".yml", ".yaml",
    ".toml", ".properties", ".md", ".pro",
}

# Carpetas que se excluyen completamente (binarios, IDE, caché)
EXCLUDE_DIRS = {
    ".git", ".github",          # excluimos .github del árbol; incluimos ci.yml por extensión
    ".gradle", ".idea",
    "build", "__pycache__",
    ".zencoder", ".zenflow",
    "node_modules",
}

# Archivos concretos a excluir
EXCLUDE_FILES = {
    "auditoria_codigos.txt",
    "generar_auditoria.py",
}

# ── Helpers ───────────────────────────────────────────────────────────────────

def should_exclude_dir(path: Path) -> bool:
    return any(part in EXCLUDE_DIRS for part in path.parts)

def collect_files(root: Path) -> list[Path]:
    """Recorre el árbol y devuelve archivos ordenados (dirs primero, luego nombre)."""
    result = []
    for dirpath, dirnames, filenames in os.walk(root):
        current = Path(dirpath)
        # Filtrar subdirectorios excluidos in-place (evita descender en ellos)
        dirnames[:] = sorted(
            d for d in dirnames
            if d not in EXCLUDE_DIRS
        )
        if should_exclude_dir(current.relative_to(root)):
            continue
        for fname in sorted(filenames):
            fpath = current / fname
            if fname not in EXCLUDE_FILES and fpath.suffix in INCLUDE_EXTENSIONS:
                result.append(fpath)
    return result

def build_tree(root: Path) -> str:
    """Genera un árbol de directorios estilo 'tree' con todos los archivos relevantes."""
    lines = [f"{root.name}/"]
    def _walk(directory: Path, prefix: str):
        try:
            entries = sorted(directory.iterdir(), key=lambda p: (p.is_file(), p.name))
        except PermissionError:
            return
        entries = [
            e for e in entries
            if e.name not in EXCLUDE_DIRS
            and e.name not in EXCLUDE_FILES
            and not should_exclude_dir(e.relative_to(root))
            and (e.is_dir() or e.suffix in INCLUDE_EXTENSIONS)
        ]
        for i, entry in enumerate(entries):
            connector = "└── " if i == len(entries) - 1 else "├── "
            lines.append(f"{prefix}{connector}{entry.name}{'/' if entry.is_dir() else ''}")
            if entry.is_dir():
                extension = "    " if i == len(entries) - 1 else "│   "
                _walk(entry, prefix + extension)
    _walk(root, "")
    return "\n".join(lines)

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    files = collect_files(ROOT)
    tree  = build_tree(ROOT)

    sep_thick = "=" * 80
    sep_thin  = "-" * 80

    with open(OUTPUT, "w", encoding="utf-8") as out:

        # ── Cabecera ──────────────────────────────────────────────────────────
        out.write(f"{sep_thick}\n")
        out.write("  AUDITORÍA DE CÓDIGO — DriverShield\n")
        out.write(f"  Generado: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        out.write(f"  Raíz del proyecto: {ROOT}\n")
        out.write(f"  Total de archivos volcados: {len(files)}\n")
        out.write(f"{sep_thick}\n\n")

        # ── Prompt de contexto para el LLM ───────────────────────────────────
        out.write("CONTEXTO PARA EL MODELO LLM\n")
        out.write(sep_thin + "\n")
        out.write(
            "Este archivo contiene la totalidad del código fuente del proyecto Android\n"
            "'DriverShield' — app nativa Kotlin para conductores profesionales VTC/Cabify.\n"
            "Arquitectura: Clean Architecture + MVVM + Jetpack Compose.\n"
            "Stack: Kotlin 2.0, Compose BOM 2025.05.00, Room 2.7, Hilt 2.56,\n"
            "       DataStore 1.1.4, Coroutines, AlarmManager, Foreground Service.\n"
            "Min SDK: 26 (Android 8.0)  |  Target SDK: 35 (Android 15)\n\n"
            "Revisa el código completo, identifica errores, inconsistencias arquitectónicas,\n"
            "mejoras de rendimiento, problemas de batería y desviaciones respecto al stack.\n"
        )
        out.write(f"\n{sep_thick}\n\n")

        # ── Árbol de estructura ───────────────────────────────────────────────
        out.write("ESTRUCTURA DEL PROYECTO\n")
        out.write(sep_thin + "\n")
        out.write(tree)
        out.write(f"\n\n{sep_thick}\n\n")

        # ── Índice de archivos ────────────────────────────────────────────────
        out.write("ÍNDICE DE ARCHIVOS\n")
        out.write(sep_thin + "\n")
        for i, fpath in enumerate(files, 1):
            rel = fpath.relative_to(ROOT)
            out.write(f"  {i:03d}. {rel}\n")
        out.write(f"\n{sep_thick}\n\n")

        # ── Contenido de cada archivo ─────────────────────────────────────────
        out.write("CONTENIDO DE ARCHIVOS\n")
        out.write(f"{sep_thick}\n\n")

        for fpath in files:
            rel = fpath.relative_to(ROOT)
            out.write(f"{'▶ ' + str(rel)}\n")
            out.write(sep_thin + "\n")
            try:
                content = fpath.read_text(encoding="utf-8", errors="replace").rstrip()
                out.write(content)
            except Exception as e:
                out.write(f"[ERROR al leer el archivo: {e}]")
            out.write(f"\n\n{sep_thick}\n\n")

    size_kb = OUTPUT.stat().st_size / 1024
    print(f"✓ Auditoría generada: {OUTPUT}")
    print(f"  Archivos volcados : {len(files)}")
    print(f"  Tamaño            : {size_kb:.1f} KB")

if __name__ == "__main__":
    main()
