import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.android.racecervice.OutGaugePacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OutGaugePacketTest {

    @Test
    fun testParseStandard92BytePacket() {
        val buffer = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(123456) // timeMs
        buffer.put("XRT\u0000".toByteArray(Charsets.US_ASCII)) // car
        buffer.putShort(0x0001.toShort()) // flags
        buffer.put(3.toByte()) // gear
        buffer.put(1.toByte()) // plid
        buffer.putFloat(25.0f) // speed (25 m/s = 90 km/h)
        buffer.putFloat(5500.0f) // rpm
        buffer.putFloat(1.2f) // turbo
        buffer.putFloat(90.0f) // engTemp
        buffer.putFloat(0.75f) // fuel
        buffer.putFloat(4.5f) // oilPressure
        buffer.putFloat(95.0f) // oilTemp
        buffer.putInt(0) // dashLights
        buffer.putInt(0) // showLights
        buffer.putFloat(1.0f) // throttle
        buffer.putFloat(0.0f) // brake
        buffer.putFloat(0.0f) // clutch
        val d1 = ByteArray(16)
        System.arraycopy("Stage 1".toByteArray(Charsets.US_ASCII), 0, d1, 0, "Stage 1".length)
        buffer.put(d1) // display1
        val d2 = ByteArray(16)
        System.arraycopy("BeamNG".toByteArray(Charsets.US_ASCII), 0, d2, 0, "BeamNG".length)
        buffer.put(d2) // display2

        val packet = OutGaugePacket.parse(buffer.array())
        assertNotNull(packet)
        requireNotNull(packet)
        assertEquals(123456L, packet.timeMs)
        assertEquals("XRT", packet.car)
        assertEquals(1, packet.flags)
        assertEquals(3, packet.gear)
        assertEquals(1, packet.plid)
        assertEquals(25.0f, packet.speedMps, 0.001f)
        assertEquals(90.0, packet.speedKmh.valueKmh, 0.001)
        assertEquals(5500.0f, packet.rpm, 0.001f)
        assertEquals(1.2f, packet.turbo, 0.001f)
        assertEquals("Stage 1", packet.display1)
        assertEquals("BeamNG", packet.display2)
        assertEquals(0, packet.id)
    }

    @Test
    fun testParseExtended96BytePacket() {
        val buffer = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(9999) // timeMs
        buffer.put("CAR1".toByteArray(Charsets.US_ASCII)) // car
        buffer.putShort(0.toShort()) // flags
        buffer.put(0.toByte()) // gear
        buffer.put(0.toByte()) // plid
        buffer.putFloat(10.0f) // speed (10 m/s = 36 km/h)
        buffer.putFloat(3000.0f) // rpm
        buffer.putFloat(0f) // turbo
        buffer.putFloat(80f) // engTemp
        buffer.putFloat(0.5f) // fuel
        buffer.putFloat(3f) // oilPressure
        buffer.putFloat(85f) // oilTemp
        buffer.putInt(0) // dashLights
        buffer.putInt(0) // showLights
        buffer.putFloat(0.5f) // throttle
        buffer.putFloat(0f) // brake
        buffer.putFloat(0f) // clutch
        buffer.put(ByteArray(16)) // display1
        buffer.put(ByteArray(16)) // display2
        buffer.putInt(42) // id

        val packet = OutGaugePacket.parse(buffer.array())
        assertNotNull(packet)
        requireNotNull(packet)
        assertEquals(9999L, packet.timeMs)
        assertEquals("CAR1", packet.car)
        assertEquals(36.0, packet.speedKmh.valueKmh, 0.001)
        assertEquals(42, packet.id)
    }

    @Test
    fun testParseMinimal16BytePacket() {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(500) // timeMs
        buffer.put("TEST".toByteArray(Charsets.US_ASCII)) // car
        buffer.putShort(2.toShort()) // flags
        buffer.put(1.toByte()) // gear
        buffer.put(0.toByte()) // plid
        buffer.putFloat(50.0f) // speed (50 m/s = 180 km/h)

        val packet = OutGaugePacket.parse(buffer.array())
        assertNotNull(packet)
        requireNotNull(packet)
        assertEquals(500L, packet.timeMs)
        assertEquals("TEST", packet.car)
        assertEquals(50.0f, packet.speedMps, 0.001f)
        assertEquals(180.0, packet.speedKmh.valueKmh, 0.001)
        assertEquals(0f, packet.rpm, 0.001f)
    }

    @Test
    fun testTruncatedDatagrams() {
        assertNull(OutGaugePacket.parse(ByteArray(0)))
        assertNull(OutGaugePacket.parse(ByteArray(15)))
        val arr = ByteArray(92)
        assertNull(OutGaugePacket.parse(arr, length = 15))
    }

    @Test
    fun testSpeedConversionsAndEdgeCases() {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0)
        buffer.put(ByteArray(4))
        buffer.putShort(0)
        buffer.put(0.toByte())
        buffer.put(0.toByte())
        buffer.putFloat(0.0f)

        var packet = OutGaugePacket.parse(buffer.array())!!
        assertEquals(0.0, packet.speedKmh.valueKmh, 0.0001)

        // Negative speed (e.g. reverse)
        buffer.putFloat(12, -10.0f)
        packet = OutGaugePacket.parse(buffer.array())!!
        assertEquals(0.0, packet.speedKmh.valueKmh, 0.0001)

        // NaN speed
        buffer.putFloat(12, Float.NaN)
        packet = OutGaugePacket.parse(buffer.array())!!
        assertEquals(0.0, packet.speedKmh.valueKmh, 0.0001)

        // 100 km/h = 27.777778 m/s
        buffer.putFloat(12, 100f / 3.6f)
        packet = OutGaugePacket.parse(buffer.array())!!
        assertEquals(100.0, packet.speedKmh.valueKmh, 0.001)
    }
}
