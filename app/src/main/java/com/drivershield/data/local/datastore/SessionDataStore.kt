package com.drivershield.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session_prefs")

class SessionDataStore(private val context: Context) {

    companion object {
        val KEY_ACTIVE_SESSION_ID = longPreferencesKey("active_session_id")
        val SERVICE_START_EPOCH = longPreferencesKey("service_start_epoch")
        val WEEKLY_ACCUMULATED_MS = longPreferencesKey("weekly_accumulated_ms")
        val LAST_ALERT_THRESHOLD = intPreferencesKey("last_alert_threshold")
        val KEY_SHIFT_RUNNING = booleanPreferencesKey("shift_running")
        val DRIVER_FULL_NAME = stringPreferencesKey("driver_full_name")
        val DRIVER_DNI = stringPreferencesKey("driver_dni")
        val START_HOUR = intPreferencesKey("start_hour")
        val END_HOUR = intPreferencesKey("end_hour")
        val OFF_DAYS = stringPreferencesKey("off_days")
        val ALTERNATE_OFF_DAYS = stringPreferencesKey("alternate_off_days")
        val WEEKS_TO_ROTATE = intPreferencesKey("weeks_to_rotate")
        val NEXT_ALT_REFERENCE = longPreferencesKey("next_alt_reference")
        val IS_SESSION_TAMPERED = booleanPreferencesKey("is_session_tampered")
    }

    val isSessionTampered: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[IS_SESSION_TAMPERED] ?: false }

    val activeSessionId: Flow<Long?> = context.dataStore.data
        .map { prefs -> prefs[KEY_ACTIVE_SESSION_ID] }

    val serviceStartEpoch: Flow<Long?> = context.dataStore.data
        .map { prefs -> prefs[SERVICE_START_EPOCH] }

    val weeklyAccumulatedMs: Flow<Long?> = context.dataStore.data
        .map { prefs -> prefs[WEEKLY_ACCUMULATED_MS] }

    val lastAlertThreshold: Flow<Int?> = context.dataStore.data
        .map { prefs -> prefs[LAST_ALERT_THRESHOLD] }

    val isShiftRunning: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_SHIFT_RUNNING] ?: false }

    val driverFullName: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[DRIVER_FULL_NAME] ?: "" }

    val driverDni: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[DRIVER_DNI] ?: "" }

    val startHour: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[START_HOUR] ?: 8 }

    val endHour: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[END_HOUR] ?: 16 }

    val offDays: Flow<List<Int>> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[OFF_DAYS] ?: ""
            if (raw.isEmpty()) emptyList()
            else raw.split(',').mapNotNull { it.toIntOrNull() }
        }

    val alternateOffDays: Flow<List<Int>> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[ALTERNATE_OFF_DAYS] ?: ""
            if (raw.isEmpty()) emptyList()
            else raw.split(',').mapNotNull { it.toIntOrNull() }
        }

    val weeksToRotate: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[WEEKS_TO_ROTATE] ?: 5 }

    val nextAltReference: Flow<Long> = context.dataStore.data
        .map { prefs -> prefs[NEXT_ALT_REFERENCE] ?: 0L }

    suspend fun saveNextAltReference(epochMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[NEXT_ALT_REFERENCE] = epochMs
        }
    }

    suspend fun setSessionTampered(tampered: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_SESSION_TAMPERED] = tampered
        }
    }

    suspend fun saveActiveSessionId(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_SESSION_ID] = id
            prefs[KEY_SHIFT_RUNNING] = true
            prefs[IS_SESSION_TAMPERED] = false
        }
    }

    suspend fun setServiceStartEpoch(epoch: Long) {
        context.dataStore.edit { prefs ->
            prefs[SERVICE_START_EPOCH] = epoch
        }
    }

    suspend fun setWeeklyAccumulatedMs(ms: Long) {
        context.dataStore.edit { prefs ->
            prefs[WEEKLY_ACCUMULATED_MS] = ms
        }
    }

    suspend fun setLastAlertThreshold(threshold: Int) {
        context.dataStore.edit { prefs ->
            prefs[LAST_ALERT_THRESHOLD] = threshold
        }
    }

    suspend fun saveDriverProfile(fullName: String, dni: String) {
        context.dataStore.edit { prefs ->
            prefs[DRIVER_FULL_NAME] = fullName
            prefs[DRIVER_DNI] = dni
        }
    }

    suspend fun saveFullConfig(
        startHour: Int,
        endHour: Int,
        offDays: List<Int>,
        alternateOffDays: List<Int> = emptyList(),
        weeksToRotate: Int = 5
    ) {
        context.dataStore.edit { prefs ->
            prefs[START_HOUR] = startHour
            prefs[END_HOUR] = endHour
            prefs[OFF_DAYS] = offDays.joinToString(",")
            prefs[ALTERNATE_OFF_DAYS] = alternateOffDays.joinToString(",")
            prefs[WEEKS_TO_ROTATE] = weeksToRotate
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACTIVE_SESSION_ID)
            prefs.remove(SERVICE_START_EPOCH)
            prefs.remove(WEEKLY_ACCUMULATED_MS)
            prefs.remove(LAST_ALERT_THRESHOLD)
            prefs.remove(IS_SESSION_TAMPERED)
            prefs[KEY_SHIFT_RUNNING] = false
        }
    }
}
