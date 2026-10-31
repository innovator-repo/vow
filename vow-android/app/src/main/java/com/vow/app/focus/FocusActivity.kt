package com.vow.app.focus

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.vow.app.MainActivity
import com.vow.app.data.prefs.VowPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles vow://focus/toggle (deep link and widget tap), with no UI of its own.
 * - Focus off: switches the first profile on. This only tightens protection, so it needs no unlock.
 * - Focus on: never switches off here. It opens the app on the Controls drawer; the app lock
 *   applies first, and the switch there follows the same rules as the Profiles screen.
 */
class FocusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            try {
                val prefs = VowPreferences(applicationContext)
                when (val plan = FocusLogic.externalToggle(prefs.getProfiles().first())) {
                    is FocusLogic.Toggle.TurnedOn -> {
                        prefs.saveProfiles(plan.profiles)
                        Toast.makeText(applicationContext, "Focus on", Toast.LENGTH_SHORT).show()
                    }
                    FocusLogic.Toggle.NeedsUnlockToTurnOff -> openControls()
                    FocusLogic.Toggle.NoProfile -> openControls()
                }
            } catch (_: Exception) {
                openControls()
            }
            finish()
        }
    }

    private fun openControls() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_CONTROLS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }
}
