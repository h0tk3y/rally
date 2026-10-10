package com.h0tk3y.rally.android.racecervice

import com.h0tk3y.rally.SpeedKmh
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class OutGaugePacket(
    val timeMs: Long,
    val car: String,
    val flags: Int,
    val gear: Int,
    val plid: Int,
    val speedMps: Float,
    val rpm: Float,
    val turbo: Float = 0f,
    val engTemp: Float = 0f,
    val fuel: Float = 0f,
    val oilPressure: Float = 0f,
    val oilTemp: Float = 0f,
    val dashLights: Int = 0,
    val showLights: Int = 0,
    val throttle: Float = 0f,
    val brake: Float = 0f,
    val clutch: Float = 0f,
    val display1: String = "",
    val display2: String = "",
    val id: Int = 0,
) {
    val speedKmh: SpeedKmh
        get() {
            val speed = if (speedMps.isNaN() || speedMps.isInfinite()) 0.0 else (speedMps.toDouble() * 3.6).coerceAtLeast(0.0)
            return SpeedKmh(speed)
        }

    companion object {
        const val MIN_PACKET_SIZE = 16
        const val STANDARD_PACKET_SIZE = 92
        const val EXTENDED_PACKET_SIZE = 96

        fun parse(bytes: ByteArray, length: Int = bytes.size): OutGaugePacket? {
            if (length < MIN_PACKET_SIZE || bytes.size < length) return null
            val buffer = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)

            val timeMs = buffer.int.toLong() and 0xFFFFFFFFL
            val carBytes = ByteArray(4)
            buffer.get(carBytes)
            val car = String(carBytes, Charsets.US_ASCII).trim('\u0000', ' ')
            val flags = buffer.short.toInt() and 0xFFFF
            val gear = buffer.get().toInt() and 0xFF
            val plid = buffer.get().toInt() and 0xFF
            val speedMps = buffer.float

            if (length < 20) {
                return OutGaugePacket(
                    timeMs = timeMs,
                    car = car,
                    flags = flags,
                    gear = gear,
                    plid = plid,
                    speedMps = speedMps,
                    rpm = 0f
                )
            }

            val rpm = buffer.float
            val turbo = if (buffer.remaining() >= 4) buffer.float else 0f
            val engTemp = if (buffer.remaining() >= 4) buffer.float else 0f
            val fuel = if (buffer.remaining() >= 4) buffer.float else 0f
            val oilPressure = if (buffer.remaining() >= 4) buffer.float else 0f
            val oilTemp = if (buffer.remaining() >= 4) buffer.float else 0f
            val dashLights = if (buffer.remaining() >= 4) buffer.int else 0
            val showLights = if (buffer.remaining() >= 4) buffer.int else 0
            val throttle = if (buffer.remaining() >= 4) buffer.float else 0f
            val brake = if (buffer.remaining() >= 4) buffer.float else 0f
            val clutch = if (buffer.remaining() >= 4) buffer.float else 0f

            val display1 = if (buffer.remaining() >= 16) {
                val d1Bytes = ByteArray(16)
                buffer.get(d1Bytes)
                String(d1Bytes, Charsets.US_ASCII).trim('\u0000', ' ')
            } else ""

            val display2 = if (buffer.remaining() >= 16) {
                val d2Bytes = ByteArray(16)
                buffer.get(d2Bytes)
                String(d2Bytes, Charsets.US_ASCII).trim('\u0000', ' ')
            } else ""

            val id = if (buffer.remaining() >= 4) buffer.int else 0

            return OutGaugePacket(
                timeMs = timeMs,
                car = car,
                flags = flags,
                gear = gear,
                plid = plid,
                speedMps = speedMps,
                rpm = rpm,
                turbo = turbo,
                engTemp = engTemp,
                fuel = fuel,
                oilPressure = oilPressure,
                oilTemp = oilTemp,
                dashLights = dashLights,
                showLights = showLights,
                throttle = throttle,
                brake = brake,
                clutch = clutch,
                display1 = display1,
                display2 = display2,
                id = id
            )
        }
    }
}
