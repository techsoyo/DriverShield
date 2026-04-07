package com.drivershield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drivershield.data.local.datastore.SessionDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val dataStore = SessionDataStore(context)
        CoroutineScope(Dispatchers.IO).launch {
            val isRunning = dataStore.isShiftRunning.first()
            if (isRunning) {
                val serviceIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_RECOVER_BOOT
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
