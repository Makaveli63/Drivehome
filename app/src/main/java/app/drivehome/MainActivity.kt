package app.drivehome

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            state.drive.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = AppState(applicationContext)
        state.reload()

        setContent {
            val pf = state.prefs
            LaunchedEffect(pf.keepScreenOn) { applyKeepScreenOn(pf.keepScreenOn) }
            LaunchedEffect(pf.fullscreen) { applyFullscreen(pf.fullscreen) }

            val base = LocalDensity.current
            val scale = pf.uiScale.coerceIn(60, 160) / 100f
            CompositionLocalProvider(
                LocalDensity provides Density(base.density * scale, base.fontScale),
            ) {
                DriveTheme(state) {
                    Root(state, onRequestPermissions = { askPermissions(it) })
                }
            }
        }

        if (!state.prefs.firstRunDone) {
            state.prefs.firstRunDone = true
            askPermissions(LOCATION_PERMISSIONS)
        }

        // Actions au démarrage de l'autoradio (une seule fois par démarrage du processus)
        if (!startupDone) {
            startupDone = true
            val pf = state.prefs
            val handler = Handler(Looper.getMainLooper())
            if (pf.autoResumeMusic) {
                handler.postDelayed({ state.media.resume() }, 4000)
            }
            if (pf.autoLaunchNav) {
                handler.postDelayed({ state.navPackage()?.let { state.launch(it) } }, 6000)
            }
        }
    }

    fun askPermissions(perms: Array<String>) {
        try {
            permissionLauncher.launch(perms)
        } catch (e: Exception) {
            Apps.toast(this, "Impossible de demander l'autorisation")
        }
    }

    private fun applyKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applyFullscreen(on: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onResume() {
        super.onResume()
        state.onResume()
        if (state.prefs.fullscreen) applyFullscreen(true)
    }

    override fun onPause() {
        super.onPause()
        state.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        state.onDestroy()
    }

    /** Appui sur le bouton Home pendant que le launcher est déjà ouvert. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        state.screen = Screen.Home
        state.controlCenter = false
        state.lastTouch = System.currentTimeMillis()
    }

    companion object {
        private var startupDone = false

        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        val PHONE_PERMISSIONS = arrayOf(Manifest.permission.CALL_PHONE)
    }
}
