package com.h0tk3y.rally.android.scenes

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.strRound3
import com.h0tk3y.rally.strRound5
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsScene(
    onBack: () -> Unit,
    model: SettingsViewModel,
    calibrateByCurrentDistance: Double?
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

            val lazyListState = rememberLazyListState()

            LazyColumn(Modifier.padding(padding), lazyListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { TelemetrySource(model, calibrateByCurrentDistance) }
                item { Allowance(model) }
            }
        }
    )
}

@Composable
private fun LazyItemScope.TelemetrySource(
    model: SettingsViewModel,
    calibrateByCurrentDistance: Double?
) {
    val telemetrySource by model.currentTelemetrySource.collectAsState(TelemetrySource.BT_OBD)

    val rbColors = RadioButtonDefaults.colors(MaterialTheme.colors.primary)

    Text("Telemetry source", style = MaterialTheme.typography.h6, modifier = Modifier.padding(16.dp))

    SettingsRow(
        Modifier.clickable { model.setTelemetrySource(TelemetrySource.SIMULATION) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(colors = rbColors, selected = telemetrySource == TelemetrySource.SIMULATION, onClick = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Simulation")
                Text(style = MaterialTheme.typography.caption, text = "Set the speed by using the slider in the race view")
            }
        }
    }
    if (telemetrySource == TelemetrySource.SIMULATION) {
        Row {
            Spacer(Modifier.width(48.dp))
            SendTeleToIp(model)
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
        run {
            val btMac by model.currentMacSelection.collectAsState(null)
            var text by rememberSaveable(btMac) { mutableStateOf(btMac ?: "") }
            Row {
                Spacer(Modifier.width(48.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = text,
                            onValueChange = {
                                text = it
                                model.viewModelScope.launch {
                                    model.setBtMac(it.takeIf { it.isNotEmpty() })
                                }
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

        Spacer(Modifier.height(24.dp))
        Calibration(model, calibrateByCurrentDistance)
        Spacer(Modifier.height(24.dp))
        Row {
            Spacer(Modifier.width(48.dp))
            SendTeleToIp(model)
        }
    }
}

@Composable
private fun SendTeleToIp(model: SettingsViewModel) {
    val sendTeleToIp by model.sendTeleToIp.collectAsState(null)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Send data to another device")
        Text(
            style = MaterialTheme.typography.caption,
            text = "Enter the other device's IP to send data to it. The other device must be on the same Wi-Fi network.\n" +
                    "On the other device, open 'Driver HUD' from the menu on the section list."
        )
        var text by rememberSaveable(sendTeleToIp) { mutableStateOf(sendTeleToIp ?: "") }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = text,
            onValueChange = {
                text = it
                model.viewModelScope.launch {
                    model.setSendTeleToIp(it.takeIf { it.isNotEmpty() })
                }
            },
            label = { Text("The other device's IP address") }
        )
    }
}

@Composable
private fun Calibration(model: SettingsViewModel, calibrateByCurrentDistance: Double?) {
    val calibration by model.currentCalibration.collectAsState(null)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(48.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calibration")

            Text(style = MaterialTheme.typography.caption, text = "Distance measured by the odometer divided by the roadmap distance:")

            var odoCalibrationValue by rememberSaveable(calibration) { mutableStateOf(calibration.toString()) }
            var isOdoInvalidValue by rememberSaveable { mutableStateOf(false) }
            fun updateOdoCalibrationField(newText: String) {
                isOdoInvalidValue = validateCalibrationFactor(newText) == null
                odoCalibrationValue = newText
            }
            run {
                fun trySave() {
                    val factor = validateCalibrationFactor(odoCalibrationValue)
                    if (factor == null) {
                        isOdoInvalidValue = true
                    } else {
                        model.setOdoCalibration(factor)
                    }
                }

                Row(
                    Modifier.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = odoCalibrationValue,
                        singleLine = true,
                        onValueChange = ::updateOdoCalibrationField,
                        isError = isOdoInvalidValue,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        label = { Text("Calibration factor") }
                    )
                    Button(
                        onClick = { trySave() },
                        enabled = validateCalibrationFactor(odoCalibrationValue) != null && odoCalibrationValue.isNotBlank() && !isOdoInvalidValue && odoCalibrationValue.toDoubleOrNull() != calibration
                    ) {
                        Text("Save")
                    }
                }

                if (isOdoInvalidValue) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.height(48.dp)) {
                        Text(text = "Invalid number, should be between 0.1 and 10.0", color = MaterialTheme.colors.error)
                    }
                }
            }

            if (calibrateByCurrentDistance == null || calibrateByCurrentDistance.isFinite().not()) {
                Text(
                    style = MaterialTheme.typography.caption,
                    text = "You can calibrate from the real distance by going over the ODO check route in Race Mode and opening Settings after that."
                )
            } else {
                Text(text = "Calibrate by ODO check route:")
                Text(text = "Odometer distance: ${calibrateByCurrentDistance.strRound3()}")

                var exactValueString by rememberSaveable(calibration) { mutableStateOf("") }
                var isErrorInvalidExactDistance by rememberSaveable { mutableStateOf(false) }

                OutlinedTextField(
                    value = exactValueString,
                    singleLine = true,
                    onValueChange = {
                        isErrorInvalidExactDistance = validateDistance(it) == null
                        exactValueString = it
                        if (!isErrorInvalidExactDistance) {
                            val odoFactor = calibrateByCurrentDistance.strRound3().toDouble() / exactValueString.toDouble()
                            if (odoFactor.isFinite()) {
                                updateOdoCalibrationField(odoFactor.strRound5())
                            }
                        }
                    },
                    isError = isErrorInvalidExactDistance,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    label = { Text("Exact ODO check distance") }
                )

                if (exactValueString.isNotEmpty() && isErrorInvalidExactDistance) {
                    Text(text = "Invalid number, should be a decimal like 4.25", color = MaterialTheme.colors.error)
                }
            }
        }

        val context = LocalContext.current
        IconButton(onClick = { model.pickAndSetBluetoothDevice(context) }) {
            Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, contentDescription = null)
        }
    }
}

private fun validateCalibrationFactor(string: String): Double? {
    val d = string.toDoubleOrNull() ?: return null
    return if (d in 0.01..10.0) d else null
}

private fun validateDistance(string: String): Double? = string.toDoubleOrNull()?.takeIf { it.isFinite() }

@Composable
private fun Allowance(
    model: SettingsViewModel,
) {
    val allowance by model.currentAllowance.collectAsState(null)
    val rbColors = RadioButtonDefaults.colors(MaterialTheme.colors.primary)

    Text("Allowance", style = MaterialTheme.typography.h6, modifier = Modifier.padding(16.dp))

    SettingsRow(
        Modifier.clickable { model.setAllowance(null) }
    ) {
        RadioButton(colors = rbColors, selected = allowance == null, onClick = null)
        Text("None")
    }
    SettingsRow(
        Modifier.clickable { model.setAllowance(TimeAllowance.BY_TEN_FULL) }
    ) {
        RadioButton(colors = rbColors, selected = allowance == TimeAllowance.BY_TEN_FULL, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("⌊t/10⌋")
            Text(style = MaterialTheme.typography.caption, text = "9 min → 0; 10 min → 1; 11 min → 1")
        }
    }
    SettingsRow(
        Modifier.clickable { model.setAllowance(TimeAllowance.BY_TEN_FULL_PLUS_ONE) }
    ) {
        RadioButton(colors = rbColors, selected = allowance == TimeAllowance.BY_TEN_FULL_PLUS_ONE, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("⌈t/10⌉")
            Text(style = MaterialTheme.typography.caption, text = "9 min → 1; 10 min → 1; 11 min → 2")
        }
    }
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
    val sendTeleToIp = prefs.userPreferencesFlow.map { it.sendTeleToIp }
    val currentMacSelection = prefs.userPreferencesFlow.map { it.btMac }
    val currentAllowance = prefs.userPreferencesFlow.map { it.allowance }
    val currentCalibration = prefs.userPreferencesFlow.map { it.calibration }

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

    fun setAllowance(allowance: TimeAllowance?) {
        viewModelScope.launch {
            prefs.saveTimeAllowance(allowance)
        }
    }

    fun setOdoCalibration(newCalibration: Double) {
        viewModelScope.launch {
            prefs.saveCalibrationFactor(newCalibration)
        }
    }

    fun setSendTeleToIp(newIp: String?) {
        viewModelScope.launch {
            prefs.saveSendTeleToIp(newIp)
        }
    }

    fun pickAndSetBluetoothDevice(context: Context) {
        viewModelScope.launch {
            val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    viewModelScope.launch {
                        if (ACTION_BLUETOOTH_SELECTED == intent.action) {
                            @Suppress("DEPRECATION") 
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
