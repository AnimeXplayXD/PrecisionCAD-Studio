package com.precisioncad.studio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.precisioncad.studio.cad.io.CadIncomingDocumentRouter
import com.precisioncad.studio.ui.CadWorkspaceScreen
import com.precisioncad.studio.ui.CadWorkspaceViewModel
import com.precisioncad.studio.ui.theme.PrecisionCADTheme

class MainActivity : ComponentActivity() {

    private val workspaceViewModel: CadWorkspaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyImmersiveFullScreenMode()

        if (savedInstanceState == null && !CadIncomingDocumentRouter.isIntentAlreadyHandled(intent)) {
            workspaceViewModel.handleIncomingImportIntent(intent)
        }

        setContent {
            PrecisionCADTheme {
                CadWorkspaceScreen(viewModel = workspaceViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!CadIncomingDocumentRouter.isIntentAlreadyHandled(intent)) {
            workspaceViewModel.handleIncomingImportIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFullScreenMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveFullScreenMode()
        }
    }

    fun applyImmersiveFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
