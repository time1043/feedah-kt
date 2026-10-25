package com.time1043.feedah_kt

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "feedah-kt",
    ) {
        App()
    }
}