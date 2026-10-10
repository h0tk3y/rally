package com.h0tk3y.rally.android.racecervice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.net.SocketTimeoutException

sealed interface OutGaugeReceiverState {
    data object Listening : OutGaugeReceiverState
    data object Receiving : OutGaugeReceiverState
    data object DelayedTimeout : OutGaugeReceiverState
    data class Error(val message: String) : OutGaugeReceiverState
}

class OutGaugeReceiver(
    val port: Int,
    private val scope: CoroutineScope,
    private val onPacketReceived: (OutGaugePacket) -> Unit = {},
    private val onTimeout: () -> Unit = {}
) : Closeable {
    private val _state = MutableStateFlow<OutGaugeReceiverState>(OutGaugeReceiverState.Listening)
    val state: StateFlow<OutGaugeReceiverState> = _state.asStateFlow()

    private val _packets = MutableSharedFlow<OutGaugePacket>(extraBufferCapacity = 64)
    val packets: SharedFlow<OutGaugePacket> = _packets.asSharedFlow()

    private var socket: DatagramSocket? = null
    private var job: Job? = null
    private var lastPacketTime = 0L

    fun start() {
        if (job != null) return
        job = scope.launch {
            runReceiverLoop()
        }
    }

    private suspend fun runReceiverLoop() {
        val createdSocket = try {
            withContext(Dispatchers.IO) {
                DatagramSocket(port).apply {
                    soTimeout = 500
                }
            }
        } catch (e: Exception) {
            _state.value = OutGaugeReceiverState.Error(e.localizedMessage ?: "Port $port binding failed")
            return
        }
        socket = createdSocket
        _state.value = OutGaugeReceiverState.Listening

        val watchdogJob = scope.launch {
            while (isActive) {
                delay(200)
                val now = System.currentTimeMillis()
                if (lastPacketTime > 0 && now - lastPacketTime > WATCHDOG_TIMEOUT_MS) {
                    if (_state.value == OutGaugeReceiverState.Receiving) {
                        _state.value = OutGaugeReceiverState.DelayedTimeout
                        onTimeout()
                    }
                }
            }
        }

        try {
            withContext(Dispatchers.IO) {
                val buffer = ByteArray(OutGaugePacket.EXTENDED_PACKET_SIZE + 64)
                val datagramPacket = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        createdSocket.receive(datagramPacket)
                        val length = datagramPacket.length
                        val parsed = OutGaugePacket.parse(buffer, length)
                        if (parsed != null) {
                            lastPacketTime = System.currentTimeMillis()
                            _state.value = OutGaugeReceiverState.Receiving
                            _packets.tryEmit(parsed)
                            onPacketReceived(parsed)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Timeout allows checking coroutine cancellation
                    } catch (e: SocketException) {
                        if (isActive && !createdSocket.isClosed) {
                            _state.value = OutGaugeReceiverState.Error(e.localizedMessage ?: "Socket error")
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                _state.value = OutGaugeReceiverState.Error(e.localizedMessage ?: "Receiver error")
            }
        } finally {
            watchdogJob.cancel()
            createdSocket.close()
            socket = null
        }
    }

    override fun close() {
        job?.cancel()
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }

    companion object {
        const val DEFAULT_PORT = 4444
        const val WATCHDOG_TIMEOUT_MS = 2000L
    }
}
