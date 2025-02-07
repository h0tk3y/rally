package com.h0tk3y.rally.obd

import android.util.Log
import com.github.pires.obd.commands.SpeedCommand
import com.github.pires.obd.commands.protocol.ObdProtocolCommand
import com.github.pires.obd.exceptions.NoDataException
import java.io.InputStream

class MySpeedCommand : SpeedCommand() {
    var debugData: String = ""
        private set

    override fun readRawData(`in`: InputStream?) {
        super.readRawData(`in`)
        debugData = rawData
    }

    override fun performCalculations() {
        try {
            super.performCalculations()
        } catch (e: Exception) {
            Log.e("rallySpeed", "exception in calculation", e)
            throw NoDataException()
        }
    }
}

class SelectEcuCommand : ObdProtocolCommand("AT SH 7E0") {
    override fun getFormattedResult(): String = ""

    override fun getCalculatedResult(): String = ""

    override fun getName(): String = "select ECU"
}

