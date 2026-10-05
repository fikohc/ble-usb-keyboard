package org.fikohc.espkbd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.disconnect
import espkbd.shared.generated.resources.grab_prompt
import espkbd.shared.generated.resources.grab_window_only
import espkbd.shared.generated.resources.grabbed
import espkbd.shared.generated.resources.grabbed_hint
import espkbd.shared.generated.resources.hint_soft_keyboard
import espkbd.shared.generated.resources.last_typed
import espkbd.shared.generated.resources.link_connected
import espkbd.shared.generated.resources.link_connecting
import espkbd.shared.generated.resources.link_disconnected
import espkbd.shared.generated.resources.link_disconnected_reason
import espkbd.shared.generated.resources.link_not_connected
import espkbd.shared.generated.resources.no_device
import espkbd.shared.generated.resources.permission_grant
import espkbd.shared.generated.resources.permission_needed
import espkbd.shared.generated.resources.press_a_key
import espkbd.shared.generated.resources.scanning
import espkbd.shared.generated.resources.select_device
import espkbd.shared.generated.resources.usb_host_missing
import org.fikohc.espkbd.ble.BleDevice
import org.fikohc.espkbd.ble.KableBleClient
import org.fikohc.espkbd.keys.SOFT_INPUT_SENTINEL
import org.fikohc.espkbd.keys.diffSoftInput
import org.fikohc.espkbd.keys.softInputNeedsReset
import org.fikohc.espkbd.keys.firmwareName
import org.fikohc.espkbd.keys.formatKeys
import org.fikohc.espkbd.keys.parseKeyCsv
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun App(
    // Android grants Bluetooth at runtime; other platforms prompt on first use
    blePermissionGranted: Boolean = true,
    onRequestBlePermission: () -> Unit = {},
) {
    val vm = viewModel { AppViewModel(KableBleClient(), createKeyboardGrabber()) }
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
                placeholder = { Text(stringResource(Res.string.select_device)) },
                supportingText = { Text(linkLabel(state)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (state.devices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(state.scanError?.text() ?: stringResource(Res.string.scanning)) },
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
                        text = { Text(stringResource(Res.string.disconnect)) },
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
                stringResource(Res.string.usb_host_missing),
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

@Composable
private fun linkLabel(state: UiState): String = when {
    // A failed or dropped connection clears the selection; say why
    state.selected == null -> state.linkError?.let { stringResource(Res.string.link_disconnected_reason, it.text()) }
        ?: state.scanError?.text()
        ?: stringResource(Res.string.link_not_connected)
    state.link == LinkStatus.CONNECTED -> stringResource(Res.string.link_connected)
    state.link == LinkStatus.CONNECTING -> stringResource(Res.string.link_connecting)
    else -> state.linkError?.let { stringResource(Res.string.link_disconnected_reason, it.text()) }
        ?: stringResource(Res.string.link_disconnected)
}

@Composable
private fun UiError.text(): String = detail ?: stringResource(fallback)

@Composable
private fun PermissionPanel(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.permission_needed), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text(stringResource(Res.string.permission_grant)) }
    }
}

@Composable
private fun KeyPanel(state: UiState, vm: AppViewModel, modifier: Modifier) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val active = state.link == LinkStatus.CONNECTED

    LaunchedEffect(windowFocused) {
        if (!windowFocused) vm.windowFocusLost()
    }
    LaunchedEffect(active) {
        if (active) runCatching { focusRequester.requestFocus() }
    }

    val grabbed = state.grab != GrabMode.OFF
    val grabBorder = if (grabbed) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier
            .padding(12.dp)
            .border(3.dp, grabBorder, MaterialTheme.shapes.medium)
            .onFocusChanged { if (!it.hasFocus) vm.panelFocusLost() }
            .onKeyEvent { event ->
                val name = event.key.firmwareName() ?: return@onKeyEvent false
                if (event.isFromSoftKeyboard()) {
                    return@onKeyEvent true
                }
                when (event.type) {
                    KeyEventType.KeyDown -> vm.keyDown(name)
                    KeyEventType.KeyUp -> vm.keyUp(name)
                    else -> return@onKeyEvent false
                }
                true
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                runCatching { focusRequester.requestFocus() }
                if (usesSoftKeyboard) keyboard?.show()
                vm.toggleGrab()
            }
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (usesSoftKeyboard) {
            SoftKeyboardInput(vm, Modifier.focusRequester(focusRequester))
        } else {
            Box(Modifier.size(1.dp).focusRequester(focusRequester).focusable())
        }
        PanelContent(state, vm.releaseCombo)
    }
}

// Invisible text field that receives on-screen keyboard input and turns edits into taps.
// The text is kept between edits (see SoftInput.kt) so the IME keeps its shift/Caps Lock state.
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
            val edit = diffSoftInput(new.text, value.text)
            vm.softBackspace(edit.backspaces)
            vm.typeText(edit.inserted)
            value = if (softInputNeedsReset(new.text)) reset else new
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
private fun PanelContent(state: UiState, releaseCombo: StringResource?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            state.link != LinkStatus.CONNECTED -> {
                Message(stringResource(Res.string.no_device))
                if (state.link == LinkStatus.CONNECTING) Hint(stringResource(Res.string.link_connecting))
            }
            state.usbHostConnected == false -> Message(stringResource(Res.string.usb_host_missing))
            state.heldKeys.isNotEmpty() -> Text(
                formatKeys(state.heldKeys),
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            releaseCombo != null && state.grab == GrabMode.OFF ->
                Message(stringResource(Res.string.grab_prompt, stringResource(releaseCombo)))
            releaseCombo != null -> Message(stringResource(Res.string.grabbed))
            else -> {
                Message(stringResource(Res.string.press_a_key))
                Hint(stringResource(Res.string.hint_soft_keyboard))
            }
        }
        if (state.link == LinkStatus.CONNECTED && state.usbHostConnected != false) {
            if (releaseCombo != null && state.grab != GrabMode.OFF) {
                Hint(stringResource(Res.string.grabbed_hint, stringResource(releaseCombo)))
                if (state.grab == GrabMode.WINDOW) {
                    state.grabError?.let { Hint(stringResource(Res.string.grab_window_only, it.text())) }
                }
            }
            state.lastTyped?.let { Hint(stringResource(Res.string.last_typed, formatKeys(parseKeyCsv(it)))) }
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
