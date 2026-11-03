package app.drivehome

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = AppState(applicationContext)
        state.reload()
        setContent {
            DriveTheme(state) {
                Root(state)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        state.reload()
        state.media.start()
    }

    override fun onPause() {
        super.onPause()
        state.media.stop()
    }

    /** Appui sur le bouton Home pendant que le launcher est déjà ouvert. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        state.screen = Screen.Home
    }
}
