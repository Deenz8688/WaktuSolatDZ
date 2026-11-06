package com.deenzstudios.waktusolatdz.ui

import android.app.AlarmManager
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView

/** Re-check special permission after returning from Android settings. */
@Composable
internal fun rememberExactAlarmAccess(): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    val preview = LocalInspectionMode.current
    fun hasAccess() = !preview && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true)
    var allowed by remember { mutableStateOf(hasAccess()) }
    DisposableEffect(view, preview) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) allowed = hasAccess()
        }
        if (!preview) view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose {
            if (!preview) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
        }
    }
    return allowed
}
