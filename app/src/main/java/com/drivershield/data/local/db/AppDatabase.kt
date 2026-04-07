package com.drivershield.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.drivershield.data.local.db.dao.DayOverrideDao
import com.drivershield.data.local.db.dao.ShiftDao
import com.drivershield.data.local.db.dao.ShiftEventDao
import com.drivershield.data.local.db.dao.WeeklyAggregateDao
import com.drivershield.data.local.db.dao.WorkScheduleDao
import com.drivershield.data.local.db.entity.DayOverrideEntity
import com.drivershield.data.local.db.entity.ShiftEventEntity
import com.drivershield.data.local.db.entity.ShiftSessionEntity
import com.drivershield.data.local.db.entity.WeeklyAggregateEntity
import com.drivershield.data.local.db.entity.WorkScheduleEntity
import com.drivershield.data.local.db.migration.Migration1
import com.drivershield.data.local.db.migration.Migration2
import com.drivershield.data.local.db.migration.Migration3
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        ShiftSessionEntity::class, 
        WeeklyAggregateEntity::class, 
        ShiftEventEntity::class, 
        WorkScheduleEntity::class,
        DayOverrideEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shiftDao(): ShiftDao
    abstract fun weeklyAggregateDao(): WeeklyAggregateDao
    abstract fun shiftEventDao(): ShiftEventDao
    abstract fun workScheduleDao(): WorkScheduleDao
    abstract fun dayOverrideDao(): DayOverrideDao

    companion object {
        private const val DATABASE_NAME = "drivershield_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val Migration5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shift_events ADD COLUMN elapsedRealtime INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shift_events ADD COLUMN isSystemTimeReliable INTEGER NOT NULL DEFAULT 1")
            }
        }

        val Migration6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shift_sessions ADD COLUMN isTampered INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(Migration1, Migration2, Migration3, Migration5_6, Migration6_7)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideShiftDao(database: AppDatabase): ShiftDao = database.shiftDao()

    @Provides
    @Singleton
    fun provideWeeklyAggregateDao(database: AppDatabase): WeeklyAggregateDao = database.weeklyAggregateDao()

    @Provides
    @Singleton
    fun provideShiftEventDao(database: AppDatabase): ShiftEventDao = database.shiftEventDao()

    @Provides
    @Singleton
    fun provideWorkScheduleDao(database: AppDatabase): WorkScheduleDao = database.workScheduleDao()

    @Provides
    @Singleton
    fun provideDayOverrideDao(database: AppDatabase): DayOverrideDao = database.dayOverrideDao()
}
