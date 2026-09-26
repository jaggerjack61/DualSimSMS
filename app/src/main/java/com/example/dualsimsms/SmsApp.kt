package com.example.dualsimsms

import android.app.Application
import android.content.Context
import com.example.dualsimsms.data.ContactRepository
import com.example.dualsimsms.data.SettingsRepository
import com.example.dualsimsms.data.SimRepository
import com.example.dualsimsms.data.SmsRepository
import com.example.dualsimsms.telephony.SmsNotificationHelper
import com.google.android.material.color.DynamicColors

class SmsApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Material You: follow the wallpaper palette on Android 12+, falling
        // back to the brand scheme in themes.xml elsewhere.
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Receivers can start the process before an Activity has run.
        SmsNotificationHelper.ensureChannels(this)
    }
}

class AppContainer(context: Context) {

    val settingsRepository = SettingsRepository(context)
    val simRepository = SimRepository(context)
    val contactRepository = ContactRepository(context)
    val smsRepository = SmsRepository(context)
}

/** Small static accessor for contact data off the UI thread. */
object SmsAppContainer {
    fun contactName(context: Context, number: String): String? =
        (context.applicationContext as SmsApp).container.contactRepository.nameFor(number)

    fun suggest(context: Context, query: String): List<com.example.dualsimsms.data.ContactSuggestion> =
        (context.applicationContext as SmsApp).container.contactRepository.suggest(query)
}
