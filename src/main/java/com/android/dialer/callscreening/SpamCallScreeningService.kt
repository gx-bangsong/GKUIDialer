package com.android.dialer.callscreening

import android.telecom.Call
import com.android.dialer.numberinfo.database.NumberInfoDatabase

class SpamCallScreeningService : android.telecom.CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val response = CallResponse.Builder()
        response.setDisallowCall(false)
        response.setRejectCall(false)
        response.setSkipNotification(false)
        response.setSkipCallLog(false)
        respondToCall(details, response.build())
    }
}
