package com.miku.gamingsidebar.data

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootHelper {

    private var cachedRootAvailable: Boolean? = null

    fun isRootAvailable(): Boolean {
        if (cachedRootAvailable == true) return true
        val available = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            val exitCode = process.waitFor()
            exitCode == 0 && line != null && (line.contains("uid=0") || line.contains("root"))
        } catch (e: Exception) {
            false
        }
        if (available) cachedRootAvailable = true
        return available
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun isShizukuInstalledAndRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int = 1001) {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(requestCode)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun isPrivilegeAvailable(): Boolean {
        return isRootAvailable() || isShizukuAvailable()
    }

    fun getPrivilegeType(): String {
        return when {
            isRootAvailable() -> "Root (Magisk/KernelSU/APatch)"
            isShizukuAvailable() -> "Shizuku (Sin Root / Depuración Wi-Fi)"
            else -> "Ninguno"
        }
    }

    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            val exit = process.waitFor()
            exit == 0 && line != null && (line.contains("uid=0") || line.contains("root"))
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun createShizukuProcess(cmd: String): Process? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    suspend fun runCommandsAsRoot(commands: List<String>): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (isRootAvailable()) {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                for (cmd in commands) {
                    os.writeBytes("$cmd\n")
                }
                os.writeBytes("exit\n")
                os.flush()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val output = StringBuilder()
                var l: String?
                while (reader.readLine().also { l = it } != null) {
                    output.append(l).append("\n")
                }
                while (errorReader.readLine().also { l = it } != null) {
                    output.append(l).append("\n")
                }
                val exitCode = process.waitFor()
                return@withContext Pair(exitCode == 0, output.toString())
            } catch (e: Exception) {
                // Fallback to Shizuku if available
            }
        }

        if (isShizukuAvailable()) {
            try {
                val fullCmd = commands.joinToString("; ")
                val process = createShizukuProcess(fullCmd)
                if (process != null) {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                    val output = StringBuilder()
                    var l: String?
                    while (reader.readLine().also { l = it } != null) {
                        output.append(l).append("\n")
                    }
                    while (errorReader.readLine().also { l = it } != null) {
                        output.append(l).append("\n")
                    }
                    val exitCode = process.waitFor()
                    return@withContext Pair(exitCode == 0, output.toString())
                }
            } catch (e: Throwable) {
                return@withContext Pair(false, e.localizedMessage ?: "Error executing with Shizuku")
            }
        }

        Pair(false, "No root or Shizuku available")
    }

    fun executeSingleCommand(cmd: String): Boolean {
        // 1. Root Execution
        if (isRootAvailable()) {
            val rootSuccess = try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val exit = process.waitFor()
                exit == 0
            } catch (e: Exception) {
                false
            }
            if (rootSuccess) return true
        }

        // 2. Shizuku Execution (Non-Root via ADB Shell / Wireless Debugging)
        if (isShizukuAvailable()) {
            val shizukuSuccess = try {
                val process = createShizukuProcess(cmd)
                val exit = process?.waitFor() ?: -1
                exit == 0
            } catch (e: Throwable) {
                e.printStackTrace()
                false
            }
            if (shizukuSuccess) return true
        }

        // 3. Fallback standard execution
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val exit = process.waitFor()
            exit == 0
        } catch (e: Exception) {
            false
        }
    }

    fun executeCommandWithOutput(cmd: String): String {
        if (isRootAvailable()) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var l: String?
                while (reader.readLine().also { l = it } != null) {
                    output.append(l).append("\n")
                }
                process.waitFor()
                return output.toString()
            } catch (_: Exception) {}
        }
        return ""
    }
}

