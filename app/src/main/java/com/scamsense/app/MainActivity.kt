package com.scamsense.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamsense.app.ui.ScamSenseRoot
import com.scamsense.app.ui.theme.ScamSenseTheme
import com.scamsense.app.ui.theme.isAppInDarkTheme
import com.scamsense.app.util.Intents
import com.scamsense.app.calls.CallNotifications
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash hands over immediately to the in-app branded loading screen.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleShareIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            ScamSenseTheme(
                darkTheme = isAppInDarkTheme(settings.themeMode),
                textScale = settings.textScale.factor,
            ) {
                ScamSenseRoot(viewModel)
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == CallNotifications.ACTION_CHECK_CALL) {
            viewModel.requestVoiceCheck()
            return
        }
        viewModel.onSharedText(Intents.extractSharedText(intent))
    }
}
