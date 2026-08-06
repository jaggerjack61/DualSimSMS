package com.example.dualsimsms.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.dualsimsms.model.SimProfile
import com.example.dualsimsms.util.SimDefaults
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Loads active subscriptions and tracks changes (SIM inserted, removed or
 * replaced). Subscription ids are stable identifiers; slot numbers are not.
 */
class SimRepository(private val context: Context) {

    private val manager: SubscriptionManager? =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    @SuppressLint("MissingPermission")
    fun activeSubscriptions(): List<SimProfile> {
        val list = try {
            manager?.activeSubscriptionInfoList
        } catch (_: SecurityException) {
            null
        } ?: return emptyList()
        return list.map { info ->
            SimProfile(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                displayName = info.displayName?.toString() ?: SimDefaults.defaultName(info.simSlotIndex),
                carrierName = info.carrierName?.toString(),
                isActive = true
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun getSubscription(subId: Int): SimProfile? {
        if (subId < 0) return null
        return try {
            manager?.getActiveSubscriptionInfo(subId)?.let {
                SimProfile(
                    subscriptionId = it.subscriptionId,
                    slotIndex = it.simSlotIndex,
                    displayName = it.displayName?.toString() ?: SimDefaults.defaultName(it.simSlotIndex),
                    carrierName = it.carrierName?.toString(),
                    isActive = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Emits the current subscription list and re-emits whenever subscriptions
     * change. Falls back to periodic polling when the phone state permission
     * is missing or on devices without the listener callback.
     */
    fun subscriptionChanges(): Flow<List<SimProfile>> = callbackFlow {
        val canListen = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        trySend(activeSubscriptions())

        if (canListen && manager != null) {
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() {
                    trySend(activeSubscriptions())
                }
            }
            try {
                manager.addOnSubscriptionsChangedListener(listener)
                awaitClose { manager.removeOnSubscriptionsChangedListener(listener) }
            } catch (_: SecurityException) {
                poll(::trySend)
            }
        } else {
            poll(::trySend)
        }
    }

    private suspend fun poll(send: (List<SimProfile>) -> Unit) {
        while (true) {
            delay(POLL_INTERVAL_MILLIS)
            send(activeSubscriptions())
        }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 60_000L
    }
}
