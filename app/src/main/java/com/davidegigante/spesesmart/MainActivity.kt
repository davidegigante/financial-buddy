package com.davidegigante.spesesmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.davidegigante.spesesmart.ui.debug.DebugScreen
import com.davidegigante.spesesmart.ui.theme.SpeseSmartTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeseSmartTheme {
                DebugScreen()
            }
        }
    }
}
