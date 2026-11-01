package com.vietstream.max

import android.content.Context
import com.google.gson.JsonObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class AppUpdateInfo(
    val versionName: String,
    val releasePageUrl: String,
    val apkUrl: String
)

private interface GitHubReleaseApi {
    @GET("repos/temu838655-dev/VIETSTREAM-MAX/releases/latest")
    suspend fun latestRelease(): JsonObject
}

object UpdateChecker {
    private val api: GitHubReleaseApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubReleaseApi::class.java)
    }

    suspend fun check(context: Context): AppUpdateInfo? {
        return try {
            val release = api.latestRelease()
            val tag = release.get("tag_name")?.takeIf { !it.isJsonNull }?.asString ?: return null
            val latestVersion = tag.removePrefix("v")
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName.orEmpty()
            if (!isNewer(latestVersion, currentVersion)) return null

            val assets = release.getAsJsonArray("assets") ?: return null
            val apk = assets.firstOrNull { asset ->
                asset.asJsonObject.get("name")?.asString?.endsWith(".apk", true) == true
            }?.asJsonObject ?: return null
            val apkUrl = apk.get("browser_download_url")?.asString ?: return null
            val pageUrl = release.get("html_url")?.asString ?: apkUrl
            AppUpdateInfo(latestVersion, pageUrl, apkUrl)
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(value: String) = value.substringBefore("-").split(".")
            .map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
