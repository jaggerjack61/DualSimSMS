package com.example.dualsimsms.model

enum class SendState {
    SENDING,
    SENT,
    FAILED,
    NO_SERVICE,
    RADIO_OFF,
    NULL_PDU,
    NOT_PERMITTED,
    NOT_DEFAULT_HANDLER,
    MISSING_SIM
}
