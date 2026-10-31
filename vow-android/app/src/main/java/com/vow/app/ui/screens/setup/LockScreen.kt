package com.vow.app.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(repo: VowRepository, onUnlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var failures by remember { mutableIntStateOf(0) }
    var lockedFor by remember { mutableIntStateOf(0) }

    LaunchedEffect(lockedFor > 0) {
        while (lockedFor > 0) { delay(1000); lockedFor-- }
    }

    fun attempt() {
        if (lockedFor > 0 || password.isEmpty()) return
        scope.launch {
            if (repo.verifyPassword(password)) {
                haptics.confirm()
                onUnlocked()
            } else {
                haptics.reject()
                failures++
                password = ""
                if (failures % 5 == 0) {
                    lockedFor = 30
                    error = "Too many tries."
                } else error = "That's not it."
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().auroraGlow().systemBarsPadding().imePadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBubble(Icons.Default.Lock, vowAccent, size = 72.dp)
        Spacer(Modifier.height(20.dp))
        Text("Vow is locked", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your password to change your protections.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = "" },
            label = { Text("Password") },
            singleLine = true,
            enabled = lockedFor == 0,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { attempt() }),
            isError = error.isNotEmpty(),
            supportingText = {
                when {
                    lockedFor > 0 -> Text("$error Try again in ${lockedFor}s.")
                    error.isNotEmpty() -> Text(error)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        GradientButton(
            "Unlock", onClick = ::attempt,
            enabled = lockedFor == 0 && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
