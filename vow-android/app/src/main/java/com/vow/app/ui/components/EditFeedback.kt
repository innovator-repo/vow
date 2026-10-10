package com.vow.app.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vow.app.data.repository.EditResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tells the user what happened to an edit: applied, delayed 24h, or refused because sealed. */
@Composable
fun rememberEditFeedback(): (EditResult) -> Unit {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    return remember(context, haptics) {
        { result ->
            when (result) {
                EditResult.Applied -> haptics.confirm()
                is EditResult.Delayed -> {
                    haptics.tick()
                    val at = SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(result.maturesAt))
                    Toast.makeText(context, "Loosening takes 24 hours — this applies $at. Cancel it under Waiting.", Toast.LENGTH_LONG).show()
                }
                is EditResult.Refused -> {
                    haptics.reject()
                    Toast.makeText(context, "${result.reason} This can't be loosened.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
