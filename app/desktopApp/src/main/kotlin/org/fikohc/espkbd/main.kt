package org.fikohc.espkbd

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.window_title
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.window_title),
        state = rememberWindowState(width = 520.dp, height = 400.dp),
    ) {
        App()
    }
}
