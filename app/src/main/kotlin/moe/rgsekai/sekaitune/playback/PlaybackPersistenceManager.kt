/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rgsekai.sekaitune.utils.reportException
import timber.log.Timber
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackPersistenceManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val persistentStateLock = Any()
        private val persistentSaveGeneration = AtomicLong(0L)

        fun incrementGeneration(): Long = persistentSaveGeneration.incrementAndGet()

        fun getGeneration(): Long = persistentSaveGeneration.get()

        inline fun <reified T : Serializable> readPersistentObject(fileName: String): T? = readPersistentObjectInternal(fileName, T::class.java)

        @Suppress("UNCHECKED_CAST")
        fun <T : Serializable> readPersistentObjectInternal(
            fileName: String,
            clazz: Class<T>,
        ): T? {
            val persistentFile = context.filesDir.resolve(fileName)
            if (!persistentFile.exists() || !persistentFile.isFile) return null

            return synchronized(persistentStateLock) {
                runCatching {
                    persistentFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { input ->
                            val payload = input.readObject()
                            if (clazz.isInstance(payload)) {
                                payload as T
                            } else {
                                error("Unexpected persistent payload type for $fileName")
                            }
                        }
                    }
                }.onFailure {
                    Timber.tag(TAG).w(it, "Failed to read persistent file: $fileName")
                }.getOrNull()
            }
        }

        fun writePersistentObject(
            fileName: String,
            payload: Serializable,
        ) {
            val persistentFile = context.filesDir.resolve(fileName)
            val tempFile = context.filesDir.resolve("$fileName.tmp")

            synchronized(persistentStateLock) {
                runCatching {
                    FileOutputStream(tempFile).use { fos ->
                        ObjectOutputStream(fos).use { output ->
                            output.writeObject(payload)
                            output.flush()
                        }
                    }

                    if (!tempFile.renameTo(persistentFile)) {
                        if (persistentFile.exists() && !persistentFile.delete()) {
                            error("Could not replace $fileName")
                        }
                        if (!tempFile.renameTo(persistentFile)) {
                            error("Could not atomically move $fileName")
                        }
                    }
                }.onFailure {
                    runCatching { tempFile.delete() }
                    reportException(it)
                }
            }
        }

        fun clearPersistedQueueFiles() {
            persistentSaveGeneration.incrementAndGet()
            synchronized(persistentStateLock) {
                listOf(
                    PERSISTENT_QUEUE_FILE,
                    PERSISTENT_PLAYER_STATE_FILE,
                    PERSISTENT_AUTOMIX_FILE,
                ).forEach { fileName ->
                    val persistentFile = context.filesDir.resolve(fileName)
                    val tempFile = context.filesDir.resolve("$fileName.tmp")
                    runCatching {
                        if (persistentFile.exists() && !persistentFile.delete()) {
                            Timber.tag(TAG).w("Failed to delete persistent file: $fileName")
                        }
                        if (tempFile.exists() && !tempFile.delete()) {
                            Timber.tag(TAG).w("Failed to delete temporary persistent file: $fileName")
                        }
                    }.onFailure {
                        Timber.tag(TAG).w(it, "Failed to clear persistent file: $fileName")
                    }
                }
            }
        }

        companion object {
            private const val TAG = "PlaybackPersistence"
            const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
            const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
            const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        }
    }
