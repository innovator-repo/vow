package com.vow.app

import android.app.Application
import java.io.File

class VowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                crashFile(this).writeText(
                    "Vow ${packageManager.getPackageInfo(packageName, 0).versionName} · Android ${android.os.Build.VERSION.RELEASE} " +
                        "(${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})\n" +
                        "Thread: ${thread.name}\n\n" + error.stackTraceToString()
                )
            } catch (_: Exception) {}
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun crashFile(app: android.content.Context) = File(app.filesDir, "last_crash.txt")
    }
}
