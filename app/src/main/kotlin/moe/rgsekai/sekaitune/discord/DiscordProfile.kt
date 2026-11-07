/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class DiscordProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: Int? = null,
    val pronouns: String? = null,
    val bio: String? = null,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val Cdn = "https://cdn.discordapp.com"

        fun fromJson(text: String?): DiscordProfile? =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }

        suspend fun fetch(token: String): DiscordProfile {
            val me = DiscordHttp.get("/users/@me", token)
            check(me.ok) { "Discord didn't share the account (HTTP ${me.code})" }
            val user = json.parseToJsonElement(me.body).jsonObject
            val id = user.text("id") ?: error("Discord sent an account without an id")
            val username = user.text("username") ?: id
            val extra = fetchExtras(id, token)
            return DiscordProfile(
                id = id,
                username = username,
                displayName = user.text("global_name") ?: username,
                avatarUrl = avatarUrl(id, user.text("avatar")),
                bannerUrl = (extra?.text("banner") ?: user.text("banner"))?.let { "$Cdn/banners/$id/$it.png?size=600" },
                bannerColor = extra?.color() ?: user.number("accent_color"),
                pronouns = extra?.text("pronouns"),
                bio = extra?.text("bio") ?: user.text("bio"),
            )
        }

        private suspend fun fetchExtras(id: String, token: String): JsonObject? = runCatching {
            val reply = DiscordHttp.get("/users/$id/profile", token)
            if (!reply.ok) return@runCatching null
            json.parseToJsonElement(reply.body).jsonObject["user_profile"]?.jsonObject
        }.getOrNull()

        private fun avatarUrl(id: String, hash: String?): String {
            if (hash != null) return "$Cdn/avatars/$id/$hash.png?size=256"
            val index = (id.toLongOrNull()?.shr(22) ?: 0L) % 6
            return "$Cdn/embed/avatars/$index.png"
        }

        private fun JsonObject.text(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun JsonObject.number(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

        private fun JsonObject.color(): Int? =
            runCatching { this["theme_colors"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.int }.getOrNull()
                ?: number("accent_color")
    }
}

