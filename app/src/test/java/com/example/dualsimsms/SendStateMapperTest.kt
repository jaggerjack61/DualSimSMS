package com.example.dualsimsms

import android.app.Activity
import android.telephony.SmsManager
import com.example.dualsimsms.model.SendState
import com.example.dualsimsms.util.SendStateMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SendStateMapperTest {

    @Test
    fun successMapsToSent() {
        assertEquals(SendState.SENT, SendStateMapper.fromSendResult(Activity.RESULT_OK))
    }

    @Test
    fun classicErrorCodesMapToSpecificStates() {
        assertEquals(
            SendState.NO_SERVICE,
            SendStateMapper.fromSendResult(SmsManager.RESULT_ERROR_NO_SERVICE)
        )
        assertEquals(
            SendState.RADIO_OFF,
            SendStateMapper.fromSendResult(SmsManager.RESULT_ERROR_RADIO_OFF)
        )
        assertEquals(
            SendState.NULL_PDU,
            SendStateMapper.fromSendResult(SmsManager.RESULT_ERROR_NULL_PDU)
        )
        assertEquals(
            SendState.NOT_PERMITTED,
            SendStateMapper.fromSendResult(SendStateMapper.RESULT_ERROR_NOT_PERMITTED)
        )
        assertEquals(
            SendState.NOT_DEFAULT_HANDLER,
            SendStateMapper.fromSendResult(SendStateMapper.RESULT_ERROR_NOT_DEFAULT_HANDLER)
        )
    }

    @Test
    fun genericFailureIsFallback() {
        assertEquals(
            SendState.FAILED,
            SendStateMapper.fromSendResult(SmsManager.RESULT_ERROR_GENERIC_FAILURE)
        )
        assertEquals(SendState.FAILED, SendStateMapper.fromSendResult(9999))
    }
}
