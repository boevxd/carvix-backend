package com.example.carvix

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.carvix.data.repository.AuthRepository
import com.example.carvix.data.repository.SettingsRepository
import com.example.carvix.screens.MainNavigation
import com.example.carvix.ui.AppToaster
import com.example.carvix.ui.LocalAppToaster
import com.example.carvix.ui.theme.CarvixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Устанавливаем splash до super.onCreate.
        // Тема Theme.Carvix.Splash подменится на Theme.Carvix автоматически.
        val splash = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Плавное затухание машинки при выходе со splash
        splash.setOnExitAnimationListener { provider ->
            val view = provider.iconView
            ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply {
                interpolator = AnticipateInterpolator()
                duration = 250L
                doOnEnd { provider.remove() }
                start()
            }
        }

        enableEdgeToEdge()
        setContent {
            val authRepo = remember { AuthRepository(applicationContext) }
            val settings = remember { SettingsRepository(applicationContext) }
            val toaster = remember { AppToaster(applicationContext) }

            var isDark by remember { mutableStateOf(settings.isDarkTheme()) }
            var language by remember { mutableStateOf(settings.getLanguage()) }
            var isLoggedIn by remember { mutableStateOf(authRepo.isLoggedIn()) }
            var sessionKey by remember { mutableStateOf(0) }

            CompositionLocalProvider(LocalAppToaster provides toaster) {
                CarvixTheme(darkTheme = isDark, language = language) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                        if (isLoggedIn) {
                            key(sessionKey) {
                                MainNavigation(
                                    authRepo = authRepo,
                                    isDark = isDark,
                                    language = language,
                                    onToggleTheme = { v ->
                                        isDark = v; settings.setDarkTheme(v)
                                        toaster.action(if (v) "Тёмная тема" else "Светлая тема")
                                    },
                                    onChangeLanguage = { v ->
                                        language = v; settings.setLanguage(v)
                                        toaster.action("Язык: $v")
                                    },
                                    onLogout = {
                                        authRepo.clearToken()
                                        isLoggedIn = false
                                        toaster.info("Вы вышли из аккаунта")
                                    }
                                )
                            }
                        } else {
                            AuthScreen(onLoginSuccess = {
                                sessionKey++
                                isLoggedIn = true
                            })
                        }
                    }
                }
            }
        }
    }
}
