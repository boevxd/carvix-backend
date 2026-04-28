package com.example.carvix.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Глобальная отладочная система уведомлений.
 *
 * Используется для подтверждения каждого взаимодействия пользователя:
 * успешные операции, ошибки, информационные сообщения.
 *
 * Все события так же логируются в Logcat с тегом "Carvix".
 */
class AppToaster(private val context: Context) {

    fun success(message: String) {
        Log.i(TAG, "[SUCCESS] $message")
        show("✓ $message", Toast.LENGTH_SHORT)
    }

    fun error(message: String?) {
        val msg = message ?: "Неизвестная ошибка"
        Log.e(TAG, "[ERROR] $msg")
        show("✗ $msg", Toast.LENGTH_LONG)
    }

    fun info(message: String) {
        Log.d(TAG, "[INFO] $message")
        show(message, Toast.LENGTH_SHORT)
    }

    fun action(message: String) {
        Log.d(TAG, "[ACTION] $message")
        show("→ $message", Toast.LENGTH_SHORT)
    }

    private fun show(text: String, duration: Int) {
        Toast.makeText(context, text, duration).show()
    }

    companion object {
        const val TAG = "Carvix"
    }
}

val LocalAppToaster = staticCompositionLocalOf<AppToaster> {
    error("AppToaster не предоставлен. Оберни Composable в CompositionLocalProvider(LocalAppToaster provides ...)")
}

@Composable
fun rememberAppToaster(): AppToaster {
    val ctx = LocalContext.current
    return remember(ctx) { AppToaster(ctx.applicationContext) }
}
