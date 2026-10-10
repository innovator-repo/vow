package com.vow.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemScreensTest {

    /** Regression: a Galaxy S23 with no com.android.vpndialogs activity crashed the app. */
    @Test fun missingSystemDialogFallsBackInsteadOfCrashing() {
        var fellBack = false
        SystemScreens.launchForResult(
            Intent(),
            launch = { throw ActivityNotFoundException("no such activity") },
            onUnavailable = { fellBack = true }
        )
        assertTrue(fellBack)
    }

    @Test fun blockedSystemDialogFallsBack() {
        var fellBack = false
        SystemScreens.launchForResult(
            Intent(),
            launch = { throw SecurityException("not permitted") },
            onUnavailable = { fellBack = true }
        )
        assertTrue(fellBack)
    }
}
