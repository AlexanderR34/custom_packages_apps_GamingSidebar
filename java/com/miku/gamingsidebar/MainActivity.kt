package com.miku.gamingsidebar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.miku.gamingsidebar.ui.screens.GamingSettingsScreen
import com.miku.gamingsidebar.ui.theme.MikuHubTheme
import com.miku.gamingsidebar.util.LocaleHelper
import com.miku.gamingsidebar.viewmodel.HubViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: HubViewModel by viewModels()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleHelper.applyLocale(this)
        enableEdgeToEdge()
        applySystemBarsMode(!viewModel.isSetupCompleted.value)
        setHighRefreshRate()

        requestRequiredPermissions()

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.miku.gamingsidebar.service.MediaPlaybackHelper.ensureNotificationPermissions(this@MainActivity)
            com.miku.gamingsidebar.service.MediaPlaybackHelper.syncState(this@MainActivity)
        }

        setContent {
            val appLanguage by viewModel.appLanguage.collectAsState()
            val isSetupCompleted by viewModel.isSetupCompleted.collectAsState()

            androidx.compose.runtime.LaunchedEffect(appLanguage) {
                LocaleHelper.applyLocale(this@MainActivity, appLanguage)
            }

            androidx.compose.runtime.LaunchedEffect(isSetupCompleted) {
                applySystemBarsMode(!isSetupCompleted)
            }

            key(appLanguage) {
                MikuHubTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        if (!isSetupCompleted) {
                            com.miku.gamingsidebar.ui.screens.PermissionSetupScreen(
                                onSetupCompleted = {
                                    viewModel.setSetupCompleted(true)
                                    viewModel.checkPermissionAndLoad()
                                    applySystemBarsMode(false)
                                }
                            )
                        } else {
                            com.miku.gamingsidebar.ui.screens.GamingSettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    private fun applySystemBarsMode(isSetupMode: Boolean) {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isSetupMode) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setHighRefreshRate() {
        try {
            val params = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                params.preferredRefreshRate = 120f
            }
            window.attributes = params
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        applySystemBarsMode(!viewModel.isSetupCompleted.value)
        setHighRefreshRate()
        com.miku.gamingsidebar.service.GamingOverlayService.activeInstance?.handleMikuHubResumed()
        // Refresh recently played games and usage statistics silently without UI stutter
        viewModel.checkPermissionAndLoad(silent = true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applySystemBarsMode(!viewModel.isSetupCompleted.value)
        }
    }
}
