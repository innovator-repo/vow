package com.vow.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.navigation.Screen
import com.vow.app.ui.navigation.VowNavHost
import com.vow.app.ui.screens.setup.LockScreen
import com.vow.app.ui.theme.VowTheme

class MainActivity : ComponentActivity() {

    private var unlocked by mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = VowRepository(this)

        setContent {
            VowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val setupComplete by repo.prefs.isSetupComplete.collectAsState(initial = null)
                    val passwordSet by repo.prefs.isPasswordSet.collectAsState(initial = null)
                    val ready = setupComplete != null && passwordSet != null
                    LaunchedEffect(ready) {
                        if (ready && !(setupComplete == true && passwordSet == true)) unlocked = true
                    }

                    when {
                        !ready -> Box(Modifier.fillMaxSize())
                        setupComplete == true && passwordSet == true && !unlocked ->
                            LockScreen(repo, onUnlocked = { unlocked = true })
                        else -> {
                            val start = remember { if (setupComplete == true) Screen.Home.route else Screen.Setup.route }
                            VowNavHost(navController = rememberNavController(), repo = repo, startDestination = start)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS) unlocked = false
    }

    private companion object {
        const val RELOCK_AFTER_MS = 5 * 60 * 1000L
    }
}
