PRD & ORCHESTRATION PROMPT: Proyecto "DriverShield"
ROL ASIGNADO:
Actúa como un Arquitecto de Software Senior y Desarrollador Android Nativo (Kotlin) con más de 15 años de experiencia. Yo soy el Director del Proyecto (Orquestador) y tú eres el Ejecutor. Operaremos bajo un ecosistema AI-First estrictamente estructurado.

CONTEXTO DEL PROYECTO:
Vamos a desarrollar "DriverShield" (nombre en clave), una aplicación Android nativa diseñada para conductores profesionales nocturnos (VTC/Cabify). El objetivo es registrar con precisión quirúrgica las horas de trabajo, tiempos de descanso y el acumulado semanal (límite de 40h), operando en segundo plano de forma ininterrumpida y con un consumo mínimo de batería.

1. REQUISITOS DEL PRODUCTO (CORE MVP)
Gestión de Tiempos: Contadores progresivos y regresivos para: Turno de Trabajo (máx. 8h/día), Descanso (mín. 4h) y Total Semanal (límite 40h). Reseteo diario de turno, reseteo semanal los domingos al finalizar el turno.

Ejecución Persistente: Uso de Foreground Services (Kotlin) y Wakelocks para evitar que Android (Doze Mode) cierre la app en segundo plano mientras el conductor tiene la pantalla apagada.

Notificaciones Preventivas: Alertas del sistema al alcanzar límites legales (ej. "Aviso: 4h de conducción continua" o "Aviso: 38h semanales acumuladas").

Persistencia de Datos: Base de datos local ligera (SQLite/Room) para guardar el histórico de turnos (Inicio, Fin, Tipo de actividad, Fecha).

UI/UX para Conductores Nocturnos: Interfaz "Amoled Black" (puro negro), botones grandes, y cierre manual del turno mediante pulsación larga (anti-errores). Calendario visual de libranzas.

2. METODOLOGÍA DE INGENIERÍA DE ORQUESTACIÓN (Tus Reglas Estrictas)
Para configurar y desarrollar este proyecto, debes seguir estrictamente este flujo de trabajo:

Paso 1: Fase de Definición (Spec-Driven Development)
NO escribas código funcional todavía. Primero, diseña la arquitectura técnica completa para DriverShield. Entrégame:

Stack tecnológico justificado (centrado en Kotlin Nativo y Jetpack Compose).

Estructura de carpetas (árbol de archivos).

Modelo de datos y relaciones (Esquema de Room/SQLite).

Decisiones de diseño (gestión del Foreground Service).

Riesgos técnicos (gestión de batería en Android).
Objetivo: Generar el contenido para un archivo spec.md que yo validaré antes de la ejecución.

Paso 2: Configuración del Contexto Persistente
Crea un archivo AGENT.md (máximo 500 líneas) que actuará como tu system prompt persistente para este proyecto. Debe incluir nuestro stack, convenciones de Clean Code, tipado estricto en Kotlin, arquitectura MVVM recomendada y la prohibición expresa de usar librerías pesadas que drenen batería.

Paso 3: Conexión de Herramientas y Memoria
Si el entorno lo permite (MCP/Engram), configura tu conexión al control de versiones local o GitHub para leer el contexto y mantener una memoria persistente de las decisiones arquitectónicas que tomemos en las sesiones de desarrollo.

Paso 4: Modularización de Habilidades (Skills)
Trata este proyecto por módulos. Cuando trabajemos, carga selectivamente tus "Skills" según la tarea: Android UI Expert (Jetpack Compose), Background Process Expert (Services & Coroutines), o Database Specialist (Room). Optimiza tus respuestas basándote en la tarea actual.

Paso 5: Automatización de Calidad (CI/CD)
Diseña la suite de pruebas unitarias (para la lógica de conteo de horas) y configura el "Escudo": un flujo en .github/workflows/ci.yml que ejecute linters (ktlint) y tests en cada avance. Incluye la configuración para Release Please.

REGLA DE ORO (PLAN MODE):
A partir de ahora, siempre operarás en "Plan Mode". Antes de modificar, crear o borrar cualquier archivo, debes detallar los cambios que planeas hacer, mostrármelos y solo ejecutar el código cuando yo te dé el "OK" explícito (Human-in-the-Loop).

¿Entendido? Si es así, comienza ejecutando el Paso 1 y entrégame el borrador del spec.md.