package com.h0tk3y.rally.android.util

import android.content.ClipData
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.R
import kotlinx.coroutines.launch
import java.io.Closeable
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import kotlin.text.Typography.section

data class LocalIp(val address: String?, val transport: String)

class LocalIpWatcher(
    context: Context,
    private val onUpdate: (List<LocalIp>) -> Unit
) : Closeable {

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val nets = ConcurrentHashMap<Network, Pair<NetworkCapabilities?, LinkProperties?>>()

    private val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            nets[network] = cm.getNetworkCapabilities(network) to cm.getLinkProperties(network)
            publish()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val lp = nets[network]?.second ?: cm.getLinkProperties(network)
            nets[network] = caps to lp
            publish()
        }

        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            val caps = nets[network]?.first ?: cm.getNetworkCapabilities(network)
            nets[network] = caps to lp
            publish()
        }

        override fun onLost(network: Network) {
            nets.remove(network)
            publish()
        }
    }

    fun start() {
        // Matches Wi-Fi, Ethernet, Cellular, VPN… anything that provides INTERNET.
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        cm.registerNetworkCallback(req, cb)
        // onAvailable will be invoked for currently-satisfying networks shortly after registration.
    }

    private fun publish() {
        val list = nets.values.flatMap { (caps, lp) ->
            val transport = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> "Bluetooth"
                else -> "Other"
            }
            (lp?.linkAddresses ?: emptyList())
                .map { it.address }
                .filterIsInstance<Inet4Address>()                    // IPv4 only
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress } // routable on LAN
                .map { LocalIp(it.hostAddress, transport) }
        }
        onUpdate(list.distinctBy { it.address to it.transport })
    }

    override fun close() {
        runCatching { cm.unregisterNetworkCallback(cb) }
    }
}

@Composable
fun StreamingServerEmptyInfo() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(48.dp)
    ) {
        Text(stringResource(R.string.otherDeviceNetworkHint), textAlign = TextAlign.Center)
        Text(stringResource(R.string.settingsOnOtherDeviceIpHint), textAlign = TextAlign.Center)
        IpAddressDisplay()
    }
}

@Composable
fun IpAddressDisplay() {
    val context = LocalContext.current
    var ips by remember { mutableStateOf<List<LocalIp>?>(null) }

    DisposableEffect(Unit) {
        val watcher = LocalIpWatcher(context) { list ->
            // NetworkCallback runs off-main; post to main-safe state
            Handler(Looper.getMainLooper()).post { ips = list }
        }
        watcher.start()
        onDispose { watcher.close() }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (val currentIps = ips) {
            null -> Text(stringResource(R.string.detectingIpAddress), textAlign = TextAlign.Center)
            emptyList<LocalIp>() -> Text(stringResource(R.string.noNetworkFoundPleaseConnectToWiFi), textAlign = TextAlign.Center)
            else -> {
                val clipboardManager = LocalClipboard.current
                val scope = rememberCoroutineScope()
                currentIps.forEach { ip ->
                    Text(
                        modifier = Modifier.clickable {
                            scope.launch {
                                clipboardManager.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText(
                                            "IP: {${ip.address}",
                                            ip.address
                                        )
                                    )
                                )
                            }
                        },
                        text = "• ${ip.address} (${ip.transport})",
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}