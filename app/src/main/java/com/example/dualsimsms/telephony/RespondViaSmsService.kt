package com.example.dualsimsms.telephony

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.dualsimsms.ui.ComposeActivity

/**
 * Handles "respond via message" intents (e.g. answering an incoming call with
 * a text) by opening the compose screen for the given number.
 */
class RespondViaSmsService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.data?.schemeSpecificPart
        if (number != null && number.isNotBlank()) {
            startActivity(
                ComposeActivity.intent(this, number)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
