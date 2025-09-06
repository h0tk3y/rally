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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.h0tk3y.rally.R
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
                    Text(stringResource(R.string.settings))
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = { }
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

    Text(stringResource(R.string.settingsTelemetrySource), style = MaterialTheme.typography.h6, modifier = Modifier.padding(16.dp))

    val isGps = telemetrySource == TelemetrySource.GPS

    SettingsRow(
        Modifier
            .fillMaxWidth()
            .clickable { model.setTelemetrySource(TelemetrySource.GPS) }
    ) {
        RadioButton(colors = rbColors, selected = isGps, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.telemetrySourceGps))
            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.telemetrySourceGpsHint))
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
            Text(stringResource(R.string.telemetrySourceObd))
            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.telemetrySourceObdHint))
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
                            label = { Text(stringResource(R.string.settingsBtMac)) }
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
        }
    }

    SettingsRow(
        Modifier.clickable { model.setTelemetrySource(TelemetrySource.SIMULATION) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(colors = rbColors, selected = telemetrySource == TelemetrySource.SIMULATION, onClick = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.telemetrySourceSimulation))
                Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.telemetrySourceSimulationHint))
            }
        }
    }

    SettingsRow {
        SendTeleToIp(model)
    }
}

@Composable
private fun SendTeleToIp(model: SettingsViewModel) {
    val sendTeleToIp by model.sendTeleToIp.collectAsState(null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.settingsSendDataToAnotherDevice), style = MaterialTheme.typography.h6, modifier = Modifier.padding(bottom = 8.dp))
        Text(text = stringResource(R.string.settingsSendDataToAnotherDeviceHint))
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
            label = { Text(stringResource(R.string.settingsTheOtherDeviceIpAddress)) }
        )
    }
}

@Composable
private fun Calibration(model: SettingsViewModel, calibrateByCurrentDistance: Double?) {
    val calibration by model.currentCalibration.collectAsState(null)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(48.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settingsCalibration))

            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.settingsCalibrationHint))

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
                        label = { Text(stringResource(R.string.settingsCalibrationFactor)) }
                    )
                    Button(
                        onClick = { trySave() },
                        enabled = validateCalibrationFactor(odoCalibrationValue) != null && odoCalibrationValue.isNotBlank() && !isOdoInvalidValue && odoCalibrationValue.toDoubleOrNull() != calibration
                    ) {
                        Text(stringResource(R.string.saveButton))
                    }
                }

                if (isOdoInvalidValue) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.height(48.dp)) {
                        Text(text = stringResource(R.string.invalidNumberShouldBeBetween01and10), color = MaterialTheme.colors.error)
                    }
                }
            }

            if (calibrateByCurrentDistance == null || calibrateByCurrentDistance.isFinite().not()) {
                Text(
                    style = MaterialTheme.typography.caption,
                    text = stringResource(R.string.calibrateFromRouteHint)
                )
            } else {
                Text(text = stringResource(R.string.settingsCalibrateByOdoCheckRoute))
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
                    label = { Text(stringResource(R.string.settingsExactOdoCheckDistance)) }
                )

                if (exactValueString.isNotEmpty() && isErrorInvalidExactDistance) {
                    Text(text = stringResource(R.string.invalidNumberShouldBeADecimal), color = MaterialTheme.colors.error)
                }
            }
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

    Text(stringResource(R.string.settingsAllowance), style = MaterialTheme.typography.h6, modifier = Modifier.padding(16.dp))

    SettingsRow(
        Modifier.clickable { model.setAllowance(null) }
    ) {
        RadioButton(colors = rbColors, selected = allowance == null, onClick = null)
        Text(stringResource(R.string.allowanceNone))
    }
    SettingsRow(
        Modifier.clickable { model.setAllowance(TimeAllowance.BY_TEN_FULL) }
    ) {
        RadioButton(colors = rbColors, selected = allowance == TimeAllowance.BY_TEN_FULL, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.allowanceT10Down))
            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.allowanceT10DownHint))
        }
    }
    SettingsRow(
        Modifier.clickable { model.setAllowance(TimeAllowance.BY_TEN_FULL_PLUS_ONE) }
    ) {
        RadioButton(colors = rbColors, selected = allowance == TimeAllowance.BY_TEN_FULL_PLUS_ONE, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.allowanceT10Up))
            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.allowanceT10UpHint))
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
