package com.drivershield.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration1 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shift_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId INTEGER NOT NULL,
                eventType TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (sessionId) REFERENCES shift_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_shift_events_sessionId ON shift_events(sessionId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_shift_events_timestamp ON shift_events(timestamp)")
    }
}

val Migration2 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS work_schedule (
                id INTEGER PRIMARY KEY NOT NULL,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                off_days TEXT NOT NULL,
                weekly_target_ms INTEGER NOT NULL,
                daily_target_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val Migration3 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE work_schedule ADD COLUMN cycle_start_epoch INTEGER NOT NULL DEFAULT 0"
        )
    }
}
