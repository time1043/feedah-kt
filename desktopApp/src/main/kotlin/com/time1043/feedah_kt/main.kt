package com.time1043.feedah_kt

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.time1043.feedah_kt.data.db.DriverFactory
import com.time1043.feedah_kt.platform.NoopTtsPlayer

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "feedah",
        state = rememberWindowState(width = 420.dp, height = 820.dp),
    ) {
        App(DriverFactory(), NoopTtsPlayer)
    }
}
