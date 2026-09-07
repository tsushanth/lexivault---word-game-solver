package com.factory.lexivaultwordgamesolver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.factory.lexivaultwordgamesolver.ui.navigation.LexiVaultApp
import com.factory.lexivaultwordgamesolver.ui.theme.LexiVaultTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LexiVaultApplication

        setContent {
            LexiVaultTheme {
                LexiVaultApp(
                    repository = app.repository,
                    premiumManager = app.premiumManager,
                    billingManager = app.billingManager,
                    onboardingManager = app.onboardingManager
                )
            }
        }
    }
}
