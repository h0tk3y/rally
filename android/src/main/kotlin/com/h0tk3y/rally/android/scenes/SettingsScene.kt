package com.h0tk3y.rally.android.scenes

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.TelemetrySource
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsScene(
    onBack: () -> Unit,
    model: SettingsViewModel
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.surface,
                title = {
                    Text("Settings")
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {

                }
            )
        },
        content = { padding ->
            val telemetrySource by model.currentTelemetrySource.collectAsState(TelemetrySource.BT_OBD)

            val lazyListState = rememberLazyListState()
            val rbColors = RadioButtonDefaults.colors(MaterialTheme.colors.primary)

            LazyColumn(Modifier.padding(padding), lazyListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Telemetry source", style = MaterialTheme.typography.h6, modifier = Modifier.padding(16.dp))

                    SettingsRow(
                        Modifier.clickable { model.setTelemetrySource(TelemetrySource.SIMULATION) }
                    ) {
                        RadioButton(colors = rbColors, selected = telemetrySource == TelemetrySource.SIMULATION, onClick = null)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Simulation")
                            Text(style = MaterialTheme.typography.caption, text = "Set the speed by using the slider in the race view")
                        }
                    }

                    val isObd = telemetrySource == TelemetrySource.BT_OBD

                    SettingsRow(
                        Modifier
                            .fillMaxWidth()
                            .clickable { model.setTelemetrySource(TelemetrySource.BT_OBD) }
                    ) {
                        RadioButton(colors = rbColors, selected = isObd, onClick = null)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("OBD over Bluetooth")
                            Text(style = MaterialTheme.typography.caption, text = "Connect to an OBD (ELM327) unit over Bluetooth")
                        }
                    }
                    if (isObd) {
                        val btMac by model.currentMacSelection.collectAsState(null)

                        run {
                            var text = rememberSaveable(btMac) { btMac ?: "" }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(48.dp))
                                OutlinedTextField(
                                    modifier = Modifier.weight(1f),
                                    value = text,
                                    onValueChange = {
                                        text = it
                                        model.setBtMac(it.takeIf { it.isNotEmpty() })
                                    },
                                    label = { Text("Bluetooth MAC address") }
                                )

                                val context = LocalContext.current
                                IconButton(onClick = { model.pickAndSetBluetoothDevice(context) }) {
                                    Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SettingsRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

class SettingsViewModel(
    val prefs: PreferenceRepository
) : ViewModel() {
    val currentTelemetrySource = prefs.userPreferencesFlow.map { it.telemetrySource }
    val currentMacSelection = prefs.userPreferencesFlow.map { it.btMac }
    fun setTelemetrySource(newTelemetrySource: TelemetrySource) {
        viewModelScope.launch {
            prefs.saveTelemetrySource(newTelemetrySource)
        }
    }

    fun setBtMac(newMac: String?) {
        viewModelScope.launch {
            prefs.saveBtMac(newMac)
        }
    }

    fun pickAndSetBluetoothDevice(context: Context) {
        viewModelScope.launch {
            val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    viewModelScope.launch {
                        if (ACTION_BLUETOOTH_SELECTED == intent.action) {
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            setBtMac(device?.address)
                        }
                    }
                }
            }

            context.registerReceiver(bluetoothReceiver, IntentFilter(ACTION_BLUETOOTH_SELECTED))
            val bluetoothPicker = Intent("android.bluetooth.devicepicker.action.LAUNCH")
            context.startActivity(bluetoothPicker)
        }
    }
}

private const val ACTION_BLUETOOTH_SELECTED =
    "android.bluetooth.devicepicker.action.DEVICE_SELECTED"
