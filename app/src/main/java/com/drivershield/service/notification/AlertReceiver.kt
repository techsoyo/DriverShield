package com.drivershield.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        
        NotificationHelper.createHighPriorityChannel(context)
        
        when (action) {
            AlertScheduler.ACTION_ALERT_4H -> {
                NotificationHelper.showLegalAlert(
                    context, 
                    "¡Atención: 4 Horas!", 
                    "Llevas 4 horas de conducción continua. Prepara un descanso.",
                    404
                )
            }
            AlertScheduler.ACTION_ALERT_6H -> {
                NotificationHelper.showLegalAlert(
                    context, 
                    "¡Alerta: 6 Horas!", 
                    "Límite legal crítico. Llevas 6 horas. Haz una parada obligatoria.",
                    406
                )
            }
            AlertScheduler.ACTION_ALERT_8H -> {
                NotificationHelper.showLegalAlert(
                    context, 
                    "¡TURNO FINALIZADO!", 
                    "Límite diario de 8 horas alcanzado. Finaliza el turno a la brevedad.",
                    408
                )
            }
            AlertScheduler.ACTION_ALERT_38H -> {
                NotificationHelper.showLegalAlert(
                    context, 
                    "¡Aviso: 38H Semanales!", 
                    "Te acercas al límite legal de 40 horas laborables esta semana.",
                    438
                )
            }
            AlertScheduler.ACTION_ALERT_40H -> {
                NotificationHelper.showLegalAlert(
                    context, 
                    "¡LÍMITE SEMANAL SUPERADO!", 
                    "Has alcanzado las 40 horas máximas laborables esta semana.",
                    440
                )
            }
        }
    }
}
