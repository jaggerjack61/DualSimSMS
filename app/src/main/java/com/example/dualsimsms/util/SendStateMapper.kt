package com.example.dualsimsms.util

import com.example.dualsimsms.model.SendState
import android.app.Activity
import android.telephony.SmsManager

/**
 * Maps SMS result codes delivered by the telephony stack to UI-facing states.
 *
 * RESULT_ERROR_NOT_DEFAULT_HANDLER (5) and RESULT_ERROR_NOT_PERMITTED (6) are
 * framework constants that are not part of the public SDK; they are defined
 * here with their AOSP values.
 */
object SendStateMapper {

    const val RESULT_ERROR_NOT_DEFAULT_HANDLER = 5
    const val RESULT_ERROR_NOT_PERMITTED = 6

    fun fromSendResult(resultCode: Int): SendState = when (resultCode) {
        Activity.RESULT_OK -> SendState.SENT
        SmsManager.RESULT_ERROR_NO_SERVICE -> SendState.NO_SERVICE
        SmsManager.RESULT_ERROR_RADIO_OFF -> SendState.RADIO_OFF
        SmsManager.RESULT_ERROR_NULL_PDU -> SendState.NULL_PDU
        RESULT_ERROR_NOT_PERMITTED -> SendState.NOT_PERMITTED
        RESULT_ERROR_NOT_DEFAULT_HANDLER -> SendState.NOT_DEFAULT_HANDLER
        else -> SendState.FAILED
    }
}
