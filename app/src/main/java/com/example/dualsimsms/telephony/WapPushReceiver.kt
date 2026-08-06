package com.example.dualsimsms.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * MMS is not supported in the first release. This receiver must be declared
 * for the app to qualify as the default SMS handler; received WAP pushes are
 * deliberately ignored.
 */
class WapPushReceiver : BroadcastReceiver() {

    /** Default-SMS-app WAP delivery action (not exposed in this SDK stub). */
    private companion object {
        const val ACTION_WAP_PUSH_DELIVER = "android.provider.Telephony.WAP_PUSH_DELIVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WAP_PUSH_DELIVER) return
        // No-op: MMS support is out of scope.
    }
}
