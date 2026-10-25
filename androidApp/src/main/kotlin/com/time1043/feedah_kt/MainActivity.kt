package com.time1043.feedah_kt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.time1043.feedah_kt.data.db.UsageTracker
import com.time1043.feedah_kt.data.db.DriverFactory
import com.time1043.feedah_kt.platform.AndroidTtsPlayer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(DriverFactory(applicationContext), AndroidTtsPlayer(applicationContext))
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppGraph.ready) {
            UsageTracker.startAppUsage(AppGraph.appScope)
        }
    }

    override fun onPause() {
        // Mirrors the original AppState listener: backgrounding pauses both
        // timers and flushes what accumulated.
        UsageTracker.pauseAppUsage()
        UsageTracker.flushUsage()
        super.onPause()
    }
}
