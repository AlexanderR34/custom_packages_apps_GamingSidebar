package com.miku.gamingsidebar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    fun isPrivilegeAvailable(): Boolean {
        return isRootAvailable()
    }

    fun getPrivilegeType(): String {
        return when {
            isRootAvailable() -> "Root (Kernel / Magisk / KernelSU / APatch)"
            else -> "Sistema Nativo (AOSP)"
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
            false
        }
    }

    fun executeSingleCommand(cmd: String): Boolean {
        return try {
            val process = if (isRootAvailable()) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            }
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun executeCommand(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            val process = if (isRootAvailable()) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }
}
