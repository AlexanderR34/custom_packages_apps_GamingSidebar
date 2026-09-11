package com.miku.gamingsidebar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object GameCoverHelper {

    private const val PREFS_NAME = "game_covers_cache_v4"
    private val memoryCache = ConcurrentHashMap<String, String>()

    // Matches play-lh.googleusercontent.com URLs with dimensions (e.g. =w526-h296 or =w1024-h576)
    private val imgDimensionRegex = Regex("""https://play-lh\.googleusercontent\.com/([A-Za-z0-9_\-]+)=w([0-9]+)-h([0-9]+)""")
    private val genericPlayLhRegex = Regex("""https://play-lh\.googleusercontent\.com/([A-Za-z0-9_\-]+)""")

    suspend fun getCoverUrl(context: Context, packageName: String): String? = withContext(Dispatchers.IO) {
        if (packageName.isBlank() || packageName == "com.miku.gamingsidebar") return@withContext null

        // 1. Check in-memory cache
        memoryCache[packageName]?.let { return@withContext it }

        // 2. Check disk SharedPreferences cache
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(packageName, null)
        if (!cached.isNullOrEmpty()) {
            memoryCache[packageName] = cached
            return@withContext cached
        }

        // 3. Fetch from Google Play Store metadata
        try {
            val playUrl = "https://play.google.com/store/apps/details?id=$packageName&hl=es"
            val connection = URL(playUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.instanceFollowRedirects = true

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val sb = StringBuilder()
                var line: String?
                var linesRead = 0

                while (reader.readLine().also { line = it } != null && linesRead < 500) {
                    sb.append(line).append("\n")
                    linesRead++
                }
                reader.close()
                connection.disconnect()

                val html = sb.toString()

                // Find all landscape screenshots/banners (where width >= 400 and width > height)
                // This rigorously filters out square icons, ESRB/PEGI rating badges (which are portrait or small), and avatars
                val dimMatches = imgDimensionRegex.findAll(html)
                var chosenHash: String? = null

                for (m in dimMatches) {
                    val hash = m.groupValues[1]
                    val w = m.groupValues[2].toIntOrNull() ?: 0
                    val h = m.groupValues[3].toIntOrNull() ?: 0
                    if (w >= 400 && w > h && hash.length > 20) {
                        chosenHash = hash
                        break
                    }
                }

                // If no dimension tag found, search for any large unique hash after line 100
                if (chosenHash == null) {
                    val allHashes = genericPlayLhRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()
                    if (allHashes.size > 2) {
                        // Pick second or third hash (first is icon, second is often promo banner)
                        chosenHash = allHashes[1]
                    }
                }

                if (chosenHash != null) {
                    val finalUrl = "https://play-lh.googleusercontent.com/$chosenHash=w1280-h720-rw"
                    memoryCache[packageName] = finalUrl
                    prefs.edit().putString(packageName, finalUrl).apply()
                    return@withContext finalUrl
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            // Fallback on network error
        }

        null
    }
}
