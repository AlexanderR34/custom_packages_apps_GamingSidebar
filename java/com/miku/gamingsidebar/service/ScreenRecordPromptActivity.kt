package com.miku.gamingsidebar.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class ScreenRecordPromptActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        MikuGamingAccessibilityService.isWaitingForProjectionApproval = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenRecordService.start(this, result.resultCode, result.data!!)
        }
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        MikuGamingAccessibilityService.isWaitingForProjectionApproval = true
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val config = MediaProjectionConfig.createConfigForDefaultDisplay()
            projectionManager.createScreenCaptureIntent(config)
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        screenCaptureLauncher.launch(captureIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        MikuGamingAccessibilityService.isWaitingForProjectionApproval = false
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, ScreenRecordPromptActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            }
            context.startActivity(intent)
        }
    }
}
