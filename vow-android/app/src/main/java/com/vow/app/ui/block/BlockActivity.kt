package com.vow.app.ui.block

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.vow.app.data.model.ExceptionPolicy
import com.vow.app.data.model.Restriction
import com.vow.app.data.model.RestrictionType
import com.vow.app.data.prefs.VowPreferences
import com.vow.app.designsystem.pressable
import com.vow.app.ipc.LumeBus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.vow.app.ui.components.GradientButton
import com.vow.app.ui.components.StaggeredItem
import com.vow.app.ui.components.SystemScreens
import com.vow.app.ui.components.auroraGlow
import com.vow.app.ui.components.rememberHaptics
import com.vow.app.ui.theme.*

class BlockActivity : ComponentActivity() {

    private var title by mutableStateOf("")
    private var message by mutableStateOf("")
    private var restrictionType by mutableStateOf<RestrictionType?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readExtras(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            VowTheme(systemDark = true) {
                BlockScreen(title, message, restrictionType, onHome = ::goHome)
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
        restrictionType = intent.getStringExtra(EXTRA_TYPE)?.let { n -> RestrictionType.entries.find { it.name == n } }
    }

    private fun goHome() {
        SystemScreens.goHome(this)
        finish()
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_TYPE = "type"

        fun show(context: Context, title: String, message: String, type: RestrictionType? = null) {
            runCatching {
                context.startActivity(
                    Intent(context, BlockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(EXTRA_TITLE, title)
                        .putExtra(EXTRA_MESSAGE, message)
                        .putExtra(EXTRA_TYPE, type?.name)
                )
            }
        }
    }
}

@Composable
private fun BlockScreen(title: String, message: String, type: RestrictionType?, onHome: () -> Unit) {
    val haptics = rememberHaptics()
    LaunchedEffect(title) { haptics.nudge() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { VowPreferences(context) }
    // The user's own calm-state words, if this block belongs to a rule they wrote them for.
    val restriction by produceState<Restriction?>(null, type) {
        value = type?.let { t -> prefs.getRestrictions().first().find { it.type == t } }
    }
    val ren = remember { LumeBus.isInstalled(context, LumeBus.REN) }
    val katachi = remember { LumeBus.isInstalled(context, LumeBus.KATACHI) }

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
                    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(vowAccent.copy(alpha = glow), Color.Transparent)), CircleShape))
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
            restriction?.takeIf { it.reason.isNotBlank() }?.let {
                Spacer(Modifier.height(16.dp))
                Text("\u201C${it.reason}\u201D", style = ReasonStyle, textAlign = TextAlign.Center)
            }
            restriction?.takeIf { it.ifThenPlan.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.ifThenPlan, style = ReasonStyle.copy(fontSize = 15.sp), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ren || katachi) {
                Spacer(Modifier.height(24.dp))
                Text("Or turn the urge into something small", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (ren) PortalCard("Replace this urge with 5 Kanji cards in Ren (90s)", LumeBus.REN_QUICK_URI) {
                    scope.launch { prefs.updateEcoCounters(ExceptionPolicy.dayKey(System.currentTimeMillis())) { c -> c.copy(redirected = c.redirected + 1) } }
                }
                if (katachi) PortalCard("Solve 1 Tangram in Katachi", LumeBus.KATACHI_NEXT_URI) {
                    scope.launch { prefs.updateEcoCounters(ExceptionPolicy.dayKey(System.currentTimeMillis())) { c -> c.copy(redirected = c.redirected + 1) } }
                }
            }
            Spacer(Modifier.height(32.dp))
            StaggeredItem(3) {
                GradientButton("Go to home screen", onClick = onHome, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Interactive Substitution Portal card: opens the peer app's micro-session via deep link. */
@Composable
private fun PortalCard(text: String, uri: String, onOpened: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).pressable().clickable {
            val ok = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (ok) onOpened()
        }
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = VowSky, modifier = Modifier.padding(16.dp))
    }
}
