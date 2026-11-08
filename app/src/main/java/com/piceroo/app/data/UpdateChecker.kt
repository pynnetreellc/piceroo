package com.piceroo.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer build on GitHub and where to download its APK. */
data class AppUpdate(val build: Long, val apkUrl: String)

/**
 * Asks GitHub for the latest release. Release tags are "build-N" and the installed app's
 * versionCode is N, so a bigger number means there's an update. Must run off the UI thread.
 * Any failure (offline, GitHub down, rate limit) just means "no update".
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/pynnetreellc/piceroo/releases/latest"

    fun check(installedBuild: Long): AppUpdate? = try {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val release = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }

        val latest = release.getString("tag_name").removePrefix("build-").toLong()
        val assets = release.getJSONArray("assets")
        val apkUrl = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url")

        if (latest > installedBuild && apkUrl != null) AppUpdate(latest, apkUrl) else null
    } catch (e: Exception) {
        null
    }
}
