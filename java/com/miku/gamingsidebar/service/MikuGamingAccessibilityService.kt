package com.miku.gamingsidebar.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MikuGamingAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (e: Exception) {
            // Ignored
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            if (isWaitingForProjectionApproval) {
                tryAutoApproveProjectionDialog(event)
            }

            val pkg = event.packageName?.toString() ?: return
            if (pkg == applicationContext.packageName || pkg == "com.miku.gamingsidebar") {
                val className = event.className?.toString() ?: ""
                if (!className.contains("MainActivity") && !className.contains("Activity")) {
                    // Ignore Miku Hub's own overlay windows (HUD, Sidebar, dialogs) so we never deactivate game mode on touch
                    return
                }
            }

            val isGame = com.miku.gamingsidebar.data.GameRepository.isGamePackage(applicationContext, pkg)
            val isSidebarEnabled = com.miku.gamingsidebar.data.SettingsRepository(applicationContext).sidebarEnabled.value
            if (isGame && isSidebarEnabled) {
                if (GamingOverlayService.activeInstance == null) {
                    if (android.provider.Settings.canDrawOverlays(applicationContext)) {
                        GamingOverlayService.start(applicationContext, targetPackage = pkg)
                    }
                }
                GamingOverlayService.onForegroundPackageChanged(applicationContext, pkg)
            } else if (isGame) {
                // Game is running but user has disabled the Gaming Sidebar
                if (GamingOverlayService.activeInstance != null) {
                    GamingOverlayService.stop(applicationContext)
                }
            } else {
                if (GamingOverlayService.activeInstance != null) {
                    GamingOverlayService.onForegroundPackageChanged(applicationContext, pkg)
                }
            }
        }
    }

    private fun tryAutoApproveProjectionDialog(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: event?.source ?: return

        // 1. Auto-select "Toda la pantalla" / "Entire screen" if dropdown / radio is visible
        val fullScreenKeywords = listOf(
            "Toda la pantalla", "Entire screen", "Pantalla completa"
        )
        for (keyword in fullScreenKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    clickNodeOrParent(node)
                }
            }
        }

        // 2. Click positive confirmation button ("Iniciar ahora", "Start now", "Empezar", etc.)
        val positiveKeywords = listOf(
            "Empezar", "Start now", "Iniciar ahora", "Iniciar grabación", "Iniciar grabacion",
            "Iniciar", "Comenzar ahora", "Comenzar", "Start", "Aceptar"
        )
        for (keyword in positiveKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (clickNodeOrParent(node)) {
                        isWaitingForProjectionApproval = false
                        return
                    }
                }
            }
        }

        val button1Nodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/button1")
        if (!button1Nodes.isNullOrEmpty()) {
            for (node in button1Nodes) {
                if (clickNodeOrParent(node)) {
                    isWaitingForProjectionApproval = false
                    return
                }
            }
        }

        val startBtnNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.systemui:id/button_start")
        if (!startBtnNodes.isNullOrEmpty()) {
            for (node in startBtnNodes) {
                if (clickNodeOrParent(node)) {
                    isWaitingForProjectionApproval = false
                    return
                }
            }
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    override fun onInterrupt() {
        // Ignored
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun captureScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }

    fun openQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    companion object {
        var instance: MikuGamingAccessibilityService? = null
            private set
        var isWaitingForProjectionApproval: Boolean = false

        /**
         * Verifica si la ventana del juego sigue presente/visible en el árbol de ventanas,
         * por ejemplo cuando el usuario tiene abierta una burbuja de chat o ventana flotante sobre el juego.
         */
        fun isGameStillVisible(gamePkg: String?): Boolean {
            if (gamePkg.isNullOrBlank()) return false
            val inst = instance ?: return false
            return try {
                val wins = inst.windows
                if (wins.isNullOrEmpty()) return false
                wins.any { win ->
                    try {
                        val root = win.root
                        val matches = root?.packageName?.toString() == gamePkg
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            @Suppress("DEPRECATION")
                            root?.recycle()
                        }
                        if (matches) return@any true

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val title = win.title?.toString()
                            if (title != null && title.contains(gamePkg, ignoreCase = true)) {
                                return@any true
                            }
                        }
                        false
                    } catch (e: Exception) {
                        false
                    }
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}
