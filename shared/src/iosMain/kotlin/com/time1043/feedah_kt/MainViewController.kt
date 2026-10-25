package com.time1043.feedah_kt

import androidx.compose.ui.window.ComposeUIViewController
import com.time1043.feedah_kt.data.db.DriverFactory
import com.time1043.feedah_kt.platform.IosTtsPlayer

fun MainViewController() = ComposeUIViewController {
    App(DriverFactory(), IosTtsPlayer())
}
