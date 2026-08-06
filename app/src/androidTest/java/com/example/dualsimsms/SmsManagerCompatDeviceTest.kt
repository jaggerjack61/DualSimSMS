package com.example.dualsimsms

import android.Manifest
import android.telephony.SubscriptionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.dualsimsms.data.SimRepository
import com.example.dualsimsms.util.SmsManagerCompat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that every active SIM can produce a subscription-bound SmsManager. */
@RunWith(AndroidJUnit4::class)
class SmsManagerCompatDeviceTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.SEND_SMS
    )

    @Test
    fun managerIsAvailableForEveryActiveSubscription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
        val subscriptions = subscriptionManager.activeSubscriptionInfoList.orEmpty()
        val repository = SimRepository(context)

        assertTrue("Expected at least one active SIM", subscriptions.isNotEmpty())
        subscriptions.forEach { subscription ->
            val subId = subscription.subscriptionId
            assertNotNull("Repository could not reload subscription $subId", repository.getSubscription(subId))
            assertNotNull(
                "No SmsManager for subscription $subId",
                SmsManagerCompat.managerFor(context, subId)
            )
        }
    }
}
