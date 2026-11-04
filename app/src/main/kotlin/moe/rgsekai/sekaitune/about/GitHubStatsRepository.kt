/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.about

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.constants.GitHubStarsCountKey
import moe.rgsekai.sekaitune.constants.GitHubTotalDownloadsKey
import moe.rgsekai.sekaitune.utils.dataStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class GitHubStats(
    val totalDownloads: Int? = null,
    val totalDownloadsFormatted: String? = null,
    val stargazersCount: Int? = null,
    val stargazersFormatted: String? = null,
)

@Singleton
class GitHubStatsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val client =
            HttpClient(OkHttp) {
                engine {
                    config {
                        connectTimeout(15, TimeUnit.SECONDS)
                        readTimeout(15, TimeUnit.SECONDS)
                        writeTimeout(15, TimeUnit.SECONDS)
                        retryOnConnectionFailure(true)
                    }
                }
            }

        suspend fun getCachedDownloads(): Int? =
            withContext(Dispatchers.IO) {
                context.dataStore.data.first()[GitHubTotalDownloadsKey]
            }

        suspend fun getCachedStars(): Int? =
            withContext(Dispatchers.IO) {
                context.dataStore.data.first()[GitHubStarsCountKey]
            }

        suspend fun fetchTotalDownloads(): Int? =
            withContext(Dispatchers.IO) {
                val prefs = context.dataStore.data.first()
                val cached = prefs[GitHubTotalDownloadsKey]

                try {
                    val response =
                        client.get("https://api.github.com/repos/$GitHubOwner/$GitHubRepo/releases?per_page=100") {
                            headers {
                                append("Accept", "application/vnd.github+json")
                                append("User-Agent", "SekaiTune")
                            }
                        }

                    if (response.status.isSuccess()) {
                        val body = response.bodyAsText()
                        val array = JSONArray(body)
                        var total = 0
                        for (i in 0 until array.length()) {
                            val releaseObj = array.getJSONObject(i)
                            val assets = releaseObj.optJSONArray("assets") ?: continue
                            for (j in 0 until assets.length()) {
                                val asset = assets.getJSONObject(j)
                                total += asset.optInt("download_count", 0)
                            }
                        }

                        if (total > 0) {
                            context.dataStore.edit { preferences ->
                                preferences[GitHubTotalDownloadsKey] = total
                            }
                            return@withContext total
                        }
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                }

                cached
            }

        suspend fun fetchStars(): Int? =
            withContext(Dispatchers.IO) {
                val prefs = context.dataStore.data.first()
                val cached = prefs[GitHubStarsCountKey]

                try {
                    val response =
                        client.get("https://api.github.com/repos/$GitHubOwner/$GitHubRepo") {
                            headers {
                                append("Accept", "application/vnd.github+json")
                                append("User-Agent", "SekaiTune")
                            }
                        }

                    if (response.status.isSuccess()) {
                        val body = response.bodyAsText()
                        val json = JSONObject(body)
                        val stars = json.optInt("stargazers_count", -1)
                        if (stars >= 0) {
                            context.dataStore.edit { preferences ->
                                preferences[GitHubStarsCountKey] = stars
                            }
                            return@withContext stars
                        }
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                }

                cached
            }

        companion object {
            private const val GitHubOwner = "rgsekai"
            private const val GitHubRepo = "sekai-tune"

            fun formatCompactNumber(count: Int): String =
                when {
                    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
                    count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0).replace(".0k", "k")
                    else -> count.toString()
                }
        }
    }

