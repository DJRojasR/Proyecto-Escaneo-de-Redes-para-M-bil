package com.lupadered.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lupadered.app.ui.LupaRoot
import com.lupadered.app.ui.theme.LupaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (application as LupaApp).ads.gatherConsent(this)
        setContent {
            LupaTheme {
                LupaRoot()
            }
        }
    }
}
