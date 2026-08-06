package com.example.dualsimsms.util

import android.content.Context
import android.telephony.SmsManager
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Creates an SmsManager bound to a specific subscription.
 *
 * Android SDK versions disagree on whether createForSubscriptionId is a
 * static factory or an instance method. Reflection supports both forms so
 * the selected subscription works across old and current devices.
 */
object SmsManagerCompat {

    private val createForSubscriptionId: Method? by lazy {
        SmsManager::class.java.methods.firstOrNull { method ->
            method.name == "createForSubscriptionId" && method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType
        }
    }

    @Suppress("DEPRECATION")
    fun managerFor(context: Context, subscriptionId: Int): SmsManager? = try {
        val method = createForSubscriptionId ?: return null
        val receiver = if (Modifier.isStatic(method.modifiers)) {
            null
        } else {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        }
        method.invoke(receiver, subscriptionId) as? SmsManager
    } catch (_: ReflectiveOperationException) {
        null
    }
}
