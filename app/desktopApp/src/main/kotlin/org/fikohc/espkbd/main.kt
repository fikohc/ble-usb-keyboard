package org.fikohc.espkbd

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ESP Keyboard",
        state = rememberWindowState(width = 520.dp, height = 400.dp),
    ) {
        App()
    }
}
