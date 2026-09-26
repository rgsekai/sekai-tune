/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.BuildConfig
import okhttp3.ConnectionPool
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val fraction: Float?,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateDownloadState
    data object Installing : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

object AppUpdateInstaller {
    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) {
        val fraction: Float?
            get() =
                totalBytes
                    .takeIf { it > 0L }
                    ?.let { downloadedBytes.toFloat() / it.toFloat() }
                    ?.coerceIn(0f, 1f)
    }

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private var activeDownloadJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
                    retryOnConnectionFailure(true)
                    followRedirects(true)
                }
            }
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun createInstallPermissionIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    fun startDownloadAndInstall(
        context: Context,
        url: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        if (activeDownloadJob?.isActive == true) return

        if (BuildConfig.DISTRIBUTION != "gms") {
            val error = IllegalStateException("In-app updates are only available for GMS builds")
            _downloadState.value = UpdateDownloadState.Error(error.message ?: "Unsupported distribution")
            onError?.invoke(error)
            return
        }

        activeDownloadJob =
            scope.launch {
                _downloadState.value = UpdateDownloadState.Downloading(null, 0L, -1L)
                try {
                    val apkFile =
                        downloadApk(context.applicationContext, url) { progress ->
                            _downloadState.value =
                                UpdateDownloadState.Downloading(
                                    fraction = progress.fraction,
                                    downloadedBytes = progress.downloadedBytes,
                                    totalBytes = progress.totalBytes,
                                )
                        }
                    _downloadState.value = UpdateDownloadState.Installing
                    withContext(Dispatchers.Main) {
                        installApk(context, apkFile)
                        onSuccess?.invoke()
                    }
                } catch (e: CancellationException) {
                    _downloadState.value = UpdateDownloadState.Idle
                    throw e
                } catch (e: Throwable) {
                    _downloadState.value = UpdateDownloadState.Error(e.message ?: "Update download failed")
                    withContext(Dispatchers.Main) {
                        onError?.invoke(e)
                    }
                }
            }
    }

    fun resetState() {
        if (activeDownloadJob?.isActive != true) {
            _downloadState.value = UpdateDownloadState.Idle
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        onProgress: (Progress) -> Unit,
    ): Result<Unit> {
        if (BuildConfig.DISTRIBUTION != "gms") {
            return Result.failure(IllegalStateException("In-app updates are only available for GMS builds"))
        }

        return try {
            val apkFile =
                withContext(Dispatchers.IO) {
                    downloadApk(context.applicationContext, url, onProgress)
                }
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Progress) -> Unit,
    ): File {
        if (url.isBlank()) {
            throw IOException("Update download URL is empty")
        }

        val updateDir = File(context.cacheDir, UpdateDirectoryName)
        updateDir.mkdirs()
        updateDir.listFiles()?.forEach { file -> file.deleteRecursively() }

        val downloadedFile = File(updateDir, DownloadFileName)

        client.prepareGet(url).execute { response ->
            val responseCode = response.status.value
            if (responseCode !in 200..299) {
                throw IOException("Update download failed: HTTP $responseCode")
            }

            val totalBytes = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            downloadedFile.outputStream().use { output ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var downloadedBytes = 0L
                var lastUpdateMs = 0L
                while (!channel.isClosedForRead) {
                    currentCoroutineContext().ensureActive()
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read.toLong()
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateMs >= PROGRESS_UPDATE_INTERVAL_MS) {
                        emitProgress(downloadedBytes, totalBytes, onProgress)
                        lastUpdateMs = now
                    }
                }
                emitProgress(downloadedBytes, totalBytes, onProgress)
            }
        }

        return if (url.lowercase(Locale.US).substringBefore('?').endsWith(".apk")) {
            downloadedFile.renameAsApk()
        } else {
            extractGmsApk(downloadedFile, File(updateDir, ApkFileName))
                ?: if (downloadedFile.containsApkManifest()) {
                    downloadedFile.renameAsApk()
                } else {
                    throw IOException("No GMS APK found in update artifact")
                }
        }
    }

    private suspend fun emitProgress(
        downloadedBytes: Long,
        totalBytes: Long,
        onProgress: (Progress) -> Unit,
    ) {
        withContext(Dispatchers.Main.immediate) {
            onProgress(Progress(downloadedBytes, totalBytes))
        }
    }

    private fun extractGmsApk(
        sourceFile: File,
        targetFile: File,
    ): File? =
        runCatching {
            ZipFile(sourceFile).use { zip ->
                val entries =
                    zip.entries().asSequence().filter { entry ->
                        val fileName = entry.name.substringAfterLast('/')
                        !entry.isDirectory &&
                            entry.name.endsWith(".apk", ignoreCase = true) &&
                            !fileName.contains("foss-", ignoreCase = true) &&
                            !fileName.contains("izzy-", ignoreCase = true)
                    }
                val preferredArtifactNames =
                    listOf(
                        "app-gms-${BuildConfig.DEVICE}-${BuildConfig.ARCHITECTURE}-",
                        "app-${BuildConfig.DEVICE}-${BuildConfig.ARCHITECTURE}-",
                    )
                val selectedEntry =
                    entries
                        .sortedBy { entry ->
                            val fileName = entry.name.substringAfterLast('/')
                            val preferredIndex =
                                preferredArtifactNames.indexOfFirst { preferredArtifactName ->
                                    fileName.contains(preferredArtifactName, ignoreCase = true)
                                }
                            if (preferredIndex >= 0) preferredIndex else preferredArtifactNames.size
                        }.firstOrNull()
                        ?: return@runCatching null

                zip.getInputStream(selectedEntry).use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
                targetFile
            }
        }.getOrNull()

    private fun File.containsApkManifest(): Boolean =
        runCatching {
            ZipFile(this).use { zip -> zip.getEntry("AndroidManifest.xml") != null }
        }.getOrDefault(false)

    private fun File.renameAsApk(): File {
        val apkFile = File(parentFile, ApkFileName)
        if (this == apkFile) return this
        if (!renameTo(apkFile)) {
            copyTo(apkFile, overwrite = true)
            delete()
        }
        return apkFile
    }

    fun installApk(
        context: Context,
        apkFile: File,
    ) {
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                apkFile,
            )
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, ApkMimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private const val UpdateDirectoryName = "app_update"
    private const val DownloadFileName = "archive-tune-update.download"
    private const val ApkFileName = "archive-tune-update.apk"
    private const val ApkMimeType = "application/vnd.android.package-archive"
    private const val STREAM_BUFFER_SIZE = 256 * 1024
    private const val PROGRESS_UPDATE_INTERVAL_MS = 200L
}
