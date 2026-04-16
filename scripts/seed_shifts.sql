-- DriverShield — Seed de turnos de prueba
-- Rango    : 01/04/2026 → 10/04/2026
-- Turno    : 18:00 CEST → 06:00 CEST día siguiente  (12h span)
-- Trabajo  : 8.5h netas (12h – 3.5h descanso 00:00-03:30)
-- durationMs = 30 600 000 ms  (8h 30m)
-- Zona     : Europe/Madrid CEST = UTC+2
--            18:00 local = 16:00 UTC | 06:00 local = 04:00 UTC siguiente día

PRAGMA foreign_keys = OFF;
BEGIN TRANSACTION;

-- ── SEMANA 14 ─────────────────────────────────────────────────────────────────

-- 01/04/2026 (Miércoles) ── start: 1775059200000 | end: 1775102400000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775059200000, 1775102400000, 'NIGHT', 30600000, 14, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775059200000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775102400000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- 02/04/2026 (Jueves) ── start: 1775145600000 | end: 1775188800000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775145600000, 1775188800000, 'NIGHT', 30600000, 14, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775145600000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775188800000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- 03/04/2026 (Viernes) ── start: 1775232000000 | end: 1775275200000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775232000000, 1775275200000, 'NIGHT', 30600000, 14, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775232000000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775275200000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- 04/04/2026 (Sábado) ── start: 1775318400000 | end: 1775361600000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775318400000, 1775361600000, 'NIGHT', 30600000, 14, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775318400000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775361600000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- 05/04/2026 (Domingo) ── start: 1775404800000 | end: 1775448000000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775404800000, 1775448000000, 'NIGHT', 30600000, 14, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775404800000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775448000000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- ── SEMANA 15 ─────────────────────────────────────────────────────────────────

-- 06/04/2026 (Lunes) ── start: 1775491200000 | end: 1775534400000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775491200000, 1775534400000, 'NIGHT', 30600000, 15, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775491200000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775534400000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- 07/04/2026 (Martes) ── start: 1775577600000 | end: 1775620800000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775577600000, 1775620800000, 'NIGHT', 30600000, 15, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775577600000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775620800000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- 08/04/2026 (Miércoles) ── start: 1775664000000 | end: 1775707200000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775664000000, 1775707200000, 'NIGHT', 30600000, 15, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775664000000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775707200000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- 09/04/2026 (Jueves) ── start: 1775750400000 | end: 1775793600000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775750400000, 1775793600000, 'NIGHT', 30600000, 15, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775750400000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775793600000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- 10/04/2026 (Viernes) ── start: 1775836800000 | end: 1775880000000
INSERT INTO shift_sessions (startTimestamp, endTimestamp, type, durationMs, isoWeekNumber, isoYear, notes, isTampered)
VALUES (1775836800000, 1775880000000, 'NIGHT', 30600000, 15, 2026, '', 0);
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'START_WORK', 1775836800000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;
INSERT INTO shift_events (sessionId, eventType, timestamp, elapsedRealtime, isSystemTimeReliable)
  SELECT id, 'END_SHIFT',  1775880000000, 0, 1 FROM shift_sessions ORDER BY id DESC LIMIT 1;

-- ── Agregados semanales ────────────────────────────────────────────────────────
-- Semana 14: 5 sesiones × 8.5h = 153 000 000 ms
INSERT OR REPLACE INTO weekly_aggregates (isoYear, isoWeekNumber, totalWorkMs, totalRestMs, sessionCount, lastUpdated)
VALUES (2026, 14, 153000000, 0, 5, 1775448000000);

-- Semana 15: 5 sesiones × 8.5h = 153 000 000 ms
INSERT OR REPLACE INTO weekly_aggregates (isoYear, isoWeekNumber, totalWorkMs, totalRestMs, sessionCount, lastUpdated)
VALUES (2026, 15, 153000000, 0, 5, 1775880000000);

COMMIT;
PRAGMA foreign_keys = ON;
