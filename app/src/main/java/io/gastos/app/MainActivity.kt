package io.gastos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.gastos.app.ui.GastosNavHost
import io.gastos.app.ui.theme.GastosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GastosApp).container
        setContent {
            GastosTheme {
                GastosNavHost(container)
            }
        }
    }
}
