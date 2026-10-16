package io.gastos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.gastos.app.ui.GastosNavHost
import io.gastos.app.ui.theme.GastosTheme
import io.gastos.app.ui.theme.ThemeId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GastosApp).container
        // Leaving the app ends the tour (and marks it seen); a rotation or theme recreate does not.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!isChangingConfigurations) container.guide.onAppStopped()
            }
        })
        setContent {
            val themeId by container.prefs.themeId.collectAsState(initial = ThemeId.CLASSIC)
            GastosTheme(themeId) {
                GastosNavHost(container)
            }
        }
    }
}
