package moe.rgsekai.sekaitune.eq.autoeq

import android.content.Context
import android.net.Uri
import moe.rgsekai.sekaitune.eq.data.ParametricEQ
import moe.rgsekai.sekaitune.eq.data.ParametricEQParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class HeadphoneForm { OverEar, InEar, Earbud }

data class AutoEqEntry(
    val name: String,
    val source: String,
    val rig: String?,
    val path: String,
    val form: HeadphoneForm,
) {
    val sourceLabel: String get() = if (rig != null) "$source · $rig" else source
}

data class AutoEqModel(
    val name: String,
    val entries: List<AutoEqEntry>,
) {
    internal val searchKey: String = normalize(name)
    internal val compactKey: String = searchKey.replace(" ", "")
    val form: HeadphoneForm get() = entries.first().form
}

class AutoEqMeasurement(
    val entry: AutoEqEntry,
    val frequencies: DoubleArray,
    val raw: DoubleArray,
    val target: DoubleArray,
    val preset: ParametricEQ,
)

@Singleton
class AutoEqRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val dir: File get() = File(context.filesDir, "autoeq").apply { mkdirs() }
    private val indexMutex = Mutex()

    @Volatile private var models: List<AutoEqModel>? = null

    suspend fun models(refresh: Boolean = false): List<AutoEqModel> = indexMutex.withLock {
        if (!refresh) models?.let { return it }
        withContext(Dispatchers.IO) {
            val file = File(dir, "INDEX.md")
            val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > IndexMaxAgeMs
            val text = if (refresh || stale) {
                runCatching { download("$ResultsBase/INDEX.md") }
                    .onSuccess { file.writeText(it) }
                    .getOrNull() ?: file.takeIf { it.exists() }?.readText()
                    ?: throw IOException("Couldn't reach the AutoEq database")
            } else {
                file.readText()
            }
            parseIndex(text).also { models = it }
        }
    }

    suspend fun measurement(entry: AutoEqEntry): AutoEqMeasurement = withContext(Dispatchers.IO) {
        val folder = entry.path.substringAfterLast('/')
        val base = "$ResultsBase/" + entry.path.split('/').joinToString("/") { Uri.encode(it) }
        val cacheKey = entry.path.hashCode().toUInt().toString(16)
        val csv = cached("$cacheKey.csv") { download("$base/${Uri.encode("$folder.csv")}") }
        val txt = cached("$cacheKey.txt") { download("$base/${Uri.encode("$folder ParametricEQ.txt")}") }
        parseMeasurement(entry, csv, txt)
    }

    private inline fun cached(name: String, fetch: () -> String): String {
        val file = File(dir, name)
        if (file.exists() && file.length() > 0) return file.readText()
        return fetch().also { file.writeText(it) }
    }

    private fun download(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "SekaiTune").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("AutoEq ${response.code} for $url")
            return response.body?.string() ?: throw IOException("Empty AutoEq response")
        }
    }

    companion object {
        private const val ResultsBase = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
        private const val IndexMaxAgeMs = 7L * 24 * 60 * 60 * 1000

        fun search(all: List<AutoEqModel>, query: String): List<AutoEqModel> {
            val q = normalize(query)
            if (q.isBlank()) return all
            val words = q.split(' ').filter { it.isNotEmpty() }
            val compact = q.replace(" ", "")
            return all.filter { model ->
                words.all { model.searchKey.contains(it) } || model.compactKey.contains(compact)
            }.sortedBy { if (it.searchKey.startsWith(q) || it.compactKey.startsWith(compact)) 0 else 1 }
        }

        internal fun parseIndex(text: String): List<AutoEqModel> {
            val entries = ArrayList<AutoEqEntry>(9000)
            text.lineSequence().forEach { line ->
                if (!line.startsWith("- [")) return@forEach
                val nameEnd = line.indexOf("](./")
                if (nameEnd < 0) return@forEach
                val pathEnd = line.indexOf(") by ", nameEnd)
                if (pathEnd < 0) return@forEach
                val name = line.substring(3, nameEnd)
                val path = percentDecode(line.substring(nameEnd + 4, pathEnd))
                val by = line.substring(pathEnd + 5).trim()
                val on = by.indexOf(" on ")
                val source = if (on >= 0) by.substring(0, on) else by
                val rig = if (on >= 0) by.substring(on + 4) else null
                val rigFolder = path.split('/').getOrNull(1).orEmpty().lowercase()
                val form = when {
                    "earbud" in rigFolder -> HeadphoneForm.Earbud
                    "in-ear" in rigFolder -> HeadphoneForm.InEar
                    else -> HeadphoneForm.OverEar
                }
                entries += AutoEqEntry(name, source, rig, path, form)
            }
            return entries
                .groupBy { it.name }
                .map { (name, list) -> AutoEqModel(name, list) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        internal fun parseMeasurement(entry: AutoEqEntry, csv: String, txt: String): AutoEqMeasurement {
            val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
            val header = lines.first().split(',').map { it.trim().lowercase() }
            val fi = header.indexOf("frequency")
            val ri = header.indexOf("raw")
            val ti = header.indexOf("target")
            if (fi < 0 || ri < 0) throw IOException("Unreadable AutoEq measurement")
            val rows = lines.drop(1).mapNotNull { row ->
                val cells = row.split(',')
                val f = cells.getOrNull(fi)?.toDoubleOrNull() ?: return@mapNotNull null
                val r = cells.getOrNull(ri)?.toDoubleOrNull() ?: return@mapNotNull null
                val t = if (ti >= 0) cells.getOrNull(ti)?.toDoubleOrNull() ?: 0.0 else 0.0
                Triple(f, r, t)
            }
            return AutoEqMeasurement(
                entry = entry,
                frequencies = DoubleArray(rows.size) { rows[it].first },
                raw = DoubleArray(rows.size) { rows[it].second },
                target = DoubleArray(rows.size) { rows[it].third },
                preset = ParametricEQParser.parseText(txt),
            )
        }
    }
}

internal fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val bytes = java.io.ByteArrayOutputStream(text.length)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (ch == '%' && i + 2 <= text.lastIndex) {
            val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.write(hex)
                i += 3
                continue
            }
        }
        bytes.write(ch.toString().toByteArray(Charsets.UTF_8))
        i++
    }
    return bytes.toString(Charsets.UTF_8.name())
}

internal fun normalize(text: String): String {
    val out = StringBuilder(text.length)
    var space = true
    for (ch in text) {
        if (ch.isLetterOrDigit()) {
            out.append(ch.lowercaseChar())
            space = false
        } else if (!space) {
            out.append(' ')
            space = true
        }
    }
    return out.trimEnd().toString()
}

