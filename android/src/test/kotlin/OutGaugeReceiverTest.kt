import com.h0tk3y.rally.android.racecervice.OutGaugePacket
import com.h0tk3y.rally.android.racecervice.OutGaugeReceiver
import com.h0tk3y.rally.android.racecervice.OutGaugeReceiverState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OutGaugeReceiverTest {

    private fun createSamplePacket(speedMps: Float): ByteArray {
        val buffer = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(100)
        buffer.put("CAR1".toByteArray(Charsets.US_ASCII))
        buffer.putShort(0)
        buffer.put(1.toByte())
        buffer.put(0.toByte())
        buffer.putFloat(speedMps)
        buffer.putFloat(2000f)
        return buffer.array()
    }

    @Test
    fun testReceivePacketsAndTimeout() = runBlocking {
        val testPort = 14444
        val scope = CoroutineScope(Dispatchers.IO)
        val receivedPackets = mutableListOf<OutGaugePacket>()
        var timeoutTriggered = false

        val receiver = OutGaugeReceiver(
            port = testPort,
            scope = scope,
            onPacketReceived = { receivedPackets.add(it) },
            onTimeout = { timeoutTriggered = true }
        )

        receiver.start()
        delay(100)
        assertEquals(OutGaugeReceiverState.Listening, receiver.state.value)

        // Send a packet
        val senderSocket = DatagramSocket()
        val packetBytes = createSamplePacket(20.0f)
        val datagram = DatagramPacket(packetBytes, packetBytes.size, InetAddress.getByName("127.0.0.1"), testPort)
        senderSocket.send(datagram)

        delay(150)
        assertEquals(1, receivedPackets.size)
        assertEquals(20.0f, receivedPackets[0].speedMps, 0.001f)
        assertEquals(OutGaugeReceiverState.Receiving, receiver.state.value)

        // Wait for watchdog timeout (>2000ms)
        delay(2200)
        assertEquals(OutGaugeReceiverState.DelayedTimeout, receiver.state.value)
        assertTrue(timeoutTriggered)

        receiver.close()
        senderSocket.close()
        scope.cancel()
    }

    @Test
    fun testPortBindingConflict() = runBlocking {
        val testPort = 14445
        val conflictSocket = DatagramSocket(testPort)
        val scope = CoroutineScope(Dispatchers.IO)

        val receiver = OutGaugeReceiver(
            port = testPort,
            scope = scope
        )

        receiver.start()
        delay(200)
        assertTrue(receiver.state.value is OutGaugeReceiverState.Error)

        receiver.close()
        conflictSocket.close()
        scope.cancel()
    }
}
