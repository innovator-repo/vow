package com.vow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.vow.app.data.prefs.VowPreferences
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.navigation.Screen
import com.vow.app.ui.navigation.VowNavHost
import com.vow.app.ui.theme.VowTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = VowRepository(this)
        val prefs = VowPreferences(this)

        val setupComplete = runBlocking { prefs.isSetupComplete.first() }
        val startDest = if (setupComplete) Screen.Home.route else Screen.Setup.route

        setContent {
            VowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    VowNavHost(
                        navController = navController,
                        repo = repo,
                        startDestination = startDest
                    )
                }
            }
        }
    }
}
