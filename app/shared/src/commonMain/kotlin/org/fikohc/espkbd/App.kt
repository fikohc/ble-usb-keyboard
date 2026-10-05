package org.fikohc.espkbd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fikohc.espkbd.ble.BleDevice
import org.fikohc.espkbd.ble.KableBleClient
import org.fikohc.espkbd.keys.SOFT_INPUT_SENTINEL
import org.fikohc.espkbd.keys.diffSoftInput
import org.fikohc.espkbd.keys.firmwareName
import org.fikohc.espkbd.keys.formatKeys
import org.fikohc.espkbd.keys.parseKeyCsv

@Composable
fun App(
    // Android grants Bluetooth at runtime; other platforms prompt on first use
    blePermissionGranted: Boolean = true,
    onRequestBlePermission: () -> Unit = {},
) {
    val vm = viewModel { AppViewModel(KableBleClient()) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(blePermissionGranted) { vm.setScanAllowed(blePermissionGranted) }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TopBar(state, vm)
                HorizontalDivider()
                if (!blePermissionGranted) {
                    PermissionPanel(onRequestBlePermission)
                } else {
                    KeyPanel(state, vm, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(state: UiState, vm: AppViewModel) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { vm.setPickerOpen(expanded) }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = state.selected?.name ?: "",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Device") },
                placeholder = { Text("Select device") },
                supportingText = { Text(linkLabel(state)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (state.devices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(state.scanError ?: "Scanning…") },
                        onClick = {},
                        enabled = false,
                    )
                }
                state.devices.forEach { device ->
                    DropdownMenuItem(
                        text = { DeviceRow(device) },
                        onClick = {
                            expanded = false
                            if (device != state.selected) vm.select(device)
                        },
                    )
                }
                if (state.selected != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Disconnect") },
                        onClick = {
                            expanded = false
                            vm.select(null)
                        },
                    )
                }
            }
        }
        if (state.link == LinkStatus.CONNECTED && state.usbHostConnected == false) {
            Text(
                "ESP32 is not connected to a USB host",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(device: BleDevice) {
    Column {
        Text(device.name)
        if (device.name != device.id) {
            Text(
                device.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun linkLabel(state: UiState): String = when {
    state.selected == null -> state.scanError ?: "Not connected"
    state.link == LinkStatus.CONNECTED -> "Connected"
    state.link == LinkStatus.CONNECTING -> "Connecting…"
    else -> state.linkError?.let { "Disconnected: $it" } ?: "Disconnected"
}

@Composable
private fun PermissionPanel(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bluetooth permission is needed to find the keyboard", textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant permission") }
    }
}

@Composable
private fun KeyPanel(state: UiState, vm: AppViewModel, modifier: Modifier) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val active = state.link == LinkStatus.CONNECTED

    // Keys held while the app loses focus would never see their key up
    LaunchedEffect(windowFocused) {
        if (!windowFocused) vm.releaseAll()
    }
    LaunchedEffect(active) {
        if (active) runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier
            .onFocusChanged { if (!it.hasFocus) vm.releaseAll() }
            .onPreviewKeyEvent { event ->
                val name = event.key.firmwareName() ?: return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> vm.keyDown(name)
                    KeyEventType.KeyUp -> vm.keyUp(name)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                runCatching { focusRequester.requestFocus() }
                if (usesSoftKeyboard) keyboard?.show()
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (usesSoftKeyboard) {
            SoftKeyboardInput(vm, Modifier.focusRequester(focusRequester))
        } else {
            Box(Modifier.size(1.dp).focusRequester(focusRequester).focusable())
        }
        PanelContent(state)
    }
}

// Invisible text field that receives on-screen keyboard input and turns edits into taps
@Composable
private fun SoftKeyboardInput(vm: AppViewModel, modifier: Modifier) {
    val reset = TextFieldValue(SOFT_INPUT_SENTINEL, TextRange(SOFT_INPUT_SENTINEL.length))
    var value by remember { mutableStateOf(reset) }
    BasicTextField(
        value = value,
        onValueChange = { new ->
            if (new.composition != null) {
                // Still composing (some IMEs ignore the no-suggestions hint); wait for the commit
                value = new
                return@BasicTextField
            }
            val edit = diffSoftInput(new.text)
            repeat(edit.backspaces) { vm.tapKey("backspace") }
            vm.typeText(edit.inserted)
            value = reset
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.None,
        ),
        modifier = modifier.size(1.dp).alpha(0f),
    )
}

@Composable
private fun PanelContent(state: UiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            state.link != LinkStatus.CONNECTED -> {
                Message("No device")
                if (state.link == LinkStatus.CONNECTING) Hint("Connecting…")
            }
            state.usbHostConnected == false -> Message("ESP32 is not connected to a USB host")
            state.heldKeys.isNotEmpty() -> Text(
                formatKeys(state.heldKeys),
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            else -> {
                Message("Press a key")
                Hint(if (usesSoftKeyboard) "Tap here to open the keyboard" else "Keys typed here go to the host")
            }
        }
        if (state.link == LinkStatus.CONNECTED) {
            state.lastTyped?.let { Hint("Last typed: ${formatKeys(parseKeyCsv(it))}") }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
    )
}
