package com.vow.app.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.ui.components.GradientButton
import com.vow.app.ui.components.StaggeredItem
import com.vow.app.ui.components.SystemScreens
import com.vow.app.ui.components.auroraGlow
import com.vow.app.ui.components.rememberHaptics
import com.vow.app.ui.theme.*

class BlockActivity : ComponentActivity() {

    private var title by mutableStateOf("")
    private var message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readExtras(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            VowTheme(darkTheme = true) {
                BlockScreen(title, message, onHome = ::goHome)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent) {
        title = intent.getStringExtra(EXTRA_TITLE) ?: "This app is resting"
        message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
    }

    private fun goHome() {
        SystemScreens.goHome(this)
        finish()
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"

        fun show(context: Context, title: String, message: String) {
            runCatching {
                context.startActivity(
                    Intent(context, BlockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(EXTRA_TITLE, title)
                        .putExtra(EXTRA_MESSAGE, message)
                )
            }
        }
    }
}

@Composable
private fun BlockScreen(title: String, message: String, onHome: () -> Unit) {
    val haptics = rememberHaptics()
    LaunchedEffect(title) { haptics.reject() }

    val pulse = rememberInfiniteTransition(label = "block")
    val glow by pulse.animateFloat(
        0.12f, 0.30f, infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse), label = "glow"
    )

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().auroraGlow().systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StaggeredItem(0) {
                Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(VowTeal.copy(alpha = glow), Color.Transparent)), CircleShape))
                    Box(Modifier.size(84.dp).background(AuroraBrush, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Ink, modifier = Modifier.size(40.dp))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            StaggeredItem(1) {
                Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            StaggeredItem(2) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(40.dp))
            StaggeredItem(3) {
                GradientButton("Go to home screen", onClick = onHome, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
