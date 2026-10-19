package com.h0tk3y.rally.android.scenes

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.h0tk3y.rally.R
import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.permissions.AccessPurpose
import com.h0tk3y.rally.android.permissions.TelemetryAccessController
import com.h0tk3y.rally.android.permissions.TelemetryAccessDialogHost
import com.h0tk3y.rally.android.permissions.hasGpsHardware
import com.h0tk3y.rally.android.permissions.rememberTelemetryAccessController
import com.h0tk3y.rally.android.util.isIgnoringBatteryOptimizations
import com.h0tk3y.rally.android.util.openBatteryOptimizationSettings
import com.h0tk3y.rally.android.util.openDontKillMyApp
import com.h0tk3y.rally.android.util.IpAddressDisplay
import com.h0tk3y.rally.strRound3
import com.h0tk3y.rally.strRound5
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
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
            val context = LocalContext.current
            val telemetryAccess = rememberTelemetryAccessController(onReady = { purpose ->
                if (purpose == AccessPurpose.PICK_BT_DEVICE) model.pickAndSetBluetoothDevice(context)
            })
            TelemetryAccessDialogHost(telemetryAccess, onChangeSource = null)

            LazyColumn(Modifier.padding(padding), lazyListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { TelemetrySource(model, telemetryAccess, calibrateByCurrentDistance) }
                item { Allowance(model) }
                item { SoundFeedback(model) }
                item { BackgroundReliability() }
            }
        }
    )
}

@Composable
private fun LazyItemScope.TelemetrySource(
    model: SettingsViewModel,
    telemetryAccess: TelemetryAccessController,
    calibrateByCurrentDistance: Double?
) {
    val telemetrySource by model.currentTelemetrySource.collectAsState(TelemetrySource.BT_OBD)
    val context = LocalContext.current
    val hasGps = remember(context) { context.hasGpsHardware() }

    /** Asks for the source's permissions right when the user picks it, rather than later when the race mode starts */
    fun selectSource(source: TelemetrySource) {
        model.setTelemetrySource(source)
        telemetryAccess.runWhenReady(source, AccessPurpose.NONE)
    }

    Text(stringResource(R.string.settingsTelemetrySource), style = MaterialTheme.typography.h6, modifier = Modifier.padding(16.dp))

    val isGps = telemetrySource == TelemetrySource.GPS

    SettingsRow(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = hasGps) { selectSource(TelemetrySource.GPS) }
    ) {
        RadioButton(selected = isGps, onClick = null, enabled = hasGps || isGps)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.telemetrySourceGps))
            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.telemetrySourceGpsHint))
            if (!hasGps) {
                Text(
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.error,
                    text = stringResource(R.string.telemetrySourceGpsUnavailable)
                )
            }
        }
    }
    
    val isObd = telemetrySource == TelemetrySource.BT_OBD

    SettingsRow(
        Modifier
            .fillMaxWidth()
            .clickable { selectSource(TelemetrySource.BT_OBD) }
    ) {
        RadioButton(selected = isObd, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.telemetrySourceObd))
            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.telemetrySourceObdHint))
        }
    }
    if (isObd) {
        run {
            // Not keyed by the saved value: it lags behind the typing and would reset the field mid-word
            var text by rememberSaveable { mutableStateOf<String?>(null) }
            if (text == null) {
                LaunchedEffect(Unit) { text = model.currentMacSelection.first().orEmpty() }
            }
            LaunchedEffect(model) {
                model.pickedMac.collect { text = it }
            }
            Row {
                Spacer(Modifier.width(48.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = text.orEmpty(),
                            onValueChange = {
                                text = it
                                model.viewModelScope.launch {
                                    model.setBtMac(it.takeIf { it.isNotEmpty() })
                                }
                            },
                            label = { Text(stringResource(R.string.settingsBtMac)) }
                        )

                        IconButton(onClick = {
                            // Connecting to the picked adapter needs the permission, so ask for it before picking
                            telemetryAccess.runWhenReady(TelemetrySource.BT_OBD, AccessPurpose.PICK_BT_DEVICE)
                        }) {
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

    val isOutgauge = telemetrySource == TelemetrySource.OUTGAUGE

    SettingsRow(
        Modifier
            .fillMaxWidth()
            .clickable { selectSource(TelemetrySource.OUTGAUGE) }
    ) {
        RadioButton(selected = isOutgauge, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.telemetrySourceOutgauge))
            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.telemetrySourceOutgaugeHint))
        }
    }
    if (isOutgauge) {
        val outgaugePort by model.outgaugePort.collectAsState(4444)
        var portText by rememberSaveable(outgaugePort) { mutableStateOf(outgaugePort.toString()) }
        val parsedPort = portText.toIntOrNull()
        val isValidPort = parsedPort != null && parsedPort in 1024..65535
        Row {
            Spacer(Modifier.width(48.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = portText,
                    onValueChange = {
                        portText = it
                        val p = it.toIntOrNull()
                        if (p != null && p in 1024..65535) {
                            model.setOutgaugePort(p)
                        }
                    },
                    isError = !isValidPort,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.settingsOutgaugePort)) }
                )
                if (!isValidPort) {
                    Text(
                        stringResource(R.string.invalidPortShouldBeBetween1024And65535),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }
                Text(
                    text = stringResource(R.string.settingsOutgaugeInstruction),
                    style = MaterialTheme.typography.caption
                )
                IpAddressDisplay(alignment = Alignment.Start)
            }
        }
    }

    SettingsRow(
        Modifier.clickable { selectSource(TelemetrySource.SIMULATION) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = telemetrySource == TelemetrySource.SIMULATION, onClick = null)
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
                        Text(text = stringResource(R.string.invalidNumberShouldBeBetween001and10), color = MaterialTheme.colors.error)
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
                Text(text = stringResource(R.string.settingsCalibrateOdometerDistance, calibrateByCurrentDistance.strRound3()))

                var exactValueString by rememberSaveable(calibration) { mutableStateOf("") }
                var isErrorInvalidExactDistance by rememberSaveable { mutableStateOf(false) }

                OutlinedTextField(
                    value = exactValueString,
                    singleLine = true,
                    onValueChange = {
                        isErrorInvalidExactDistance = validateDistance(it) == null
                        exactValueString = it
                        val currentFactor = calibration
                        if (!isErrorInvalidExactDistance && currentFactor != null) {
                            // The race distance has already been divided by the current factor, so undo that first
                            val rawDistance = calibrateByCurrentDistance.strRound3().toDouble() * currentFactor
                            val odoFactor = rawDistance / exactValueString.toDouble()
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

    Text(stringResource(R.string.settingsAllowance), style = MaterialTheme.typography.h6, modifier = Modifier.padding(16.dp))

    SettingsRow(
        Modifier.clickable { model.setAllowance(null) }
    ) {
        RadioButton(selected = allowance == null, onClick = null)
        Text(stringResource(R.string.allowanceNone))
    }
    SettingsRow(
        Modifier.clickable { model.setAllowance(TimeAllowance.BY_TEN_FULL) }
    ) {
        RadioButton(selected = allowance == TimeAllowance.BY_TEN_FULL, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.allowanceT10Down))
            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.allowanceT10DownHint))
        }
    }
    SettingsRow(
        Modifier.clickable { model.setAllowance(TimeAllowance.BY_TEN_FULL_PLUS_ONE) }
    ) {
        RadioButton(selected = allowance == TimeAllowance.BY_TEN_FULL_PLUS_ONE, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.allowanceT10Up))
            Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.allowanceT10UpHint))
        }
    }
}

@Composable
fun SoundFeedback(
    model: SettingsViewModel
) {
    val soundFeedback by model.soundFeedback.collectAsState(false)

    Text(stringResource(R.string.settingsRaceModeTitle), style = MaterialTheme.typography.h6, modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp))
    
    SettingsRow(
        Modifier
            .clickable { model.setSoundFeedback(!soundFeedback) }
            .fillMaxWidth()
    )  {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settingsSoundFeedback))
        }
        Spacer(Modifier.weight(1f))
        Switch(soundFeedback, onCheckedChange = model::setSoundFeedback)
    }
}

@Composable
private fun BackgroundReliability() {
    val context = LocalContext.current
    var isExempt by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    LifecycleResumeEffect(context) {
        // The user may have changed it in the system settings
        isExempt = context.isIgnoringBatteryOptimizations()
        onPauseOrDispose { }
    }

    Text(
        stringResource(R.string.settingsBackgroundReliability),
        style = MaterialTheme.typography.h6,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (isExempt) stringResource(R.string.settingsBatteryOptimizationOff)
            else stringResource(R.string.settingsBatteryOptimizationOn)
        )
        Text(style = MaterialTheme.typography.caption, text = stringResource(R.string.settingsBatteryOptimizationHint))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { context.openBatteryOptimizationSettings() }) {
                Text(stringResource(R.string.batteryButtonOpenSettings))
            }
            TextButton(onClick = { context.openDontKillMyApp() }) {
                Text(stringResource(R.string.batteryButtonDeviceTips))
            }
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
    val soundFeedback = prefs.userPreferencesFlow.map { it.soundFeedback }
    val currentMacSelection = prefs.userPreferencesFlow.map { it.btMac }
    val currentAllowance = prefs.userPreferencesFlow.map { it.allowance }
    val currentCalibration = prefs.userPreferencesFlow.map { it.calibration }
    val outgaugePort = prefs.userPreferencesFlow.map { it.outgaugePort }

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

    fun setSoundFeedback(soundFeedback: Boolean) {
        viewModelScope.launch {
            prefs.saveSoundFeedback(soundFeedback)
        }
    }

    fun setOutgaugePort(port: Int) {
        viewModelScope.launch {
            prefs.saveOutgaugePort(port)
        }
    }

    private val _pickedMac = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** The addresses the user picks with the system Bluetooth picker, to show in the MAC field */
    val pickedMac: SharedFlow<String> = _pickedMac.asSharedFlow()

    private var btPickerReceiver: Pair<Context, BroadcastReceiver>? = null

    fun pickAndSetBluetoothDevice(context: Context) {
        val appContext = context.applicationContext
        unregisterBtPickerReceiver()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_BLUETOOTH_SELECTED == intent.action) {
                    unregisterBtPickerReceiver()
                    val address = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address
                    setBtMac(address)
                    address?.let(_pickedMac::tryEmit)
                }
            }
        }
        // The picker is a part of the system, so the receiver must be exported (Android 14+ throws if this is not specified).
        // Only accept the result from a sender with the Bluetooth permission, so that other apps can't set the address.
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTION_BLUETOOTH_SELECTED),
            bluetoothSenderPermission,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
        btPickerReceiver = appContext to receiver

        try {
            context.startActivity(Intent("android.bluetooth.devicepicker.action.LAUNCH"))
        } catch (e: ActivityNotFoundException) {
            // Some vendors ship no picker
            unregisterBtPickerReceiver()
            Toast.makeText(context, R.string.settingsBtPickerUnavailable, Toast.LENGTH_LONG).show()
        }
    }

    /** The picker may be closed without a result, so the receiver is also removed with the next pick or with this view model */
    private fun unregisterBtPickerReceiver() {
        btPickerReceiver?.let { (context, receiver) -> context.unregisterReceiver(receiver) }
        btPickerReceiver = null
    }

    override fun onCleared() {
        unregisterBtPickerReceiver()
    }
}

private val bluetoothSenderPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH

private const val ACTION_BLUETOOTH_SELECTED =
    "android.bluetooth.devicepicker.action.DEVICE_SELECTED"
