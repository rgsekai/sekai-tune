/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object MusicWidgetKeys {
    val TRACK_TITLE = stringPreferencesKey("widget_track_title")
    val TRACK_ARTIST = stringPreferencesKey("widget_track_artist")
    val ART_PATH = stringPreferencesKey("widget_art_path")
    val IS_PLAYING = booleanPreferencesKey("widget_is_playing")
    val IS_BUFFERING = booleanPreferencesKey("widget_is_buffering")
    val IS_AVAILABLE = booleanPreferencesKey("widget_is_available")
    val DOMINANT_COLOR = intPreferencesKey("widget_dominant_color")
    val IS_LIKED = booleanPreferencesKey("widget_is_liked")
    val PLAYBACK_POSITION = floatPreferencesKey("widget_position")
    val LISTENING_TIME = stringPreferencesKey("widget_listening_time")
    val TOTAL_PLAYS = stringPreferencesKey("widget_total_plays")
    val RECENT_SONGS = stringPreferencesKey("widget_recent_songs")
    val GENRES = stringPreferencesKey("widget_genres")
    val RECOMMENDATIONS = stringPreferencesKey("widget_recommendations")
    val TOP_SONG_SUMMARY = stringPreferencesKey("widget_top_song_summary")
    val TRACK_MEDIA_ID = stringPreferencesKey("widget_track_media_id")
    val TRACK_DURATION = intPreferencesKey("widget_track_duration")
    val SHOW_LYRICS = booleanPreferencesKey("widget_show_lyrics")
    val LYRICS_TEXT = stringPreferencesKey("widget_lyrics_text")
    val LYRICS_STATUS = stringPreferencesKey("widget_lyrics_status")

    // Widget 4 Shortcuts
    val SHORTCUT_1_TITLE = stringPreferencesKey("widget_sc_1_title")
    val SHORTCUT_1_SUBTITLE = stringPreferencesKey("widget_sc_1_sub")
    val SHORTCUT_1_ART = stringPreferencesKey("widget_sc_1_art")
    val SHORTCUT_1_TYPE = stringPreferencesKey("widget_sc_1_type")
    val SHORTCUT_1_ID = stringPreferencesKey("widget_sc_1_id")

    val SHORTCUT_2_TITLE = stringPreferencesKey("widget_sc_2_title")
    val SHORTCUT_2_SUBTITLE = stringPreferencesKey("widget_sc_2_sub")
    val SHORTCUT_2_ART = stringPreferencesKey("widget_sc_2_art")
    val SHORTCUT_2_TYPE = stringPreferencesKey("widget_sc_2_type")
    val SHORTCUT_2_ID = stringPreferencesKey("widget_sc_2_id")

    val SHORTCUT_3_TITLE = stringPreferencesKey("widget_sc_3_title")
    val SHORTCUT_3_SUBTITLE = stringPreferencesKey("widget_sc_3_sub")
    val SHORTCUT_3_ART = stringPreferencesKey("widget_sc_3_art")
    val SHORTCUT_3_TYPE = stringPreferencesKey("widget_sc_3_type")
    val SHORTCUT_3_ID = stringPreferencesKey("widget_sc_3_id")

    val SHORTCUT_4_TITLE = stringPreferencesKey("widget_sc_4_title")
    val SHORTCUT_4_SUBTITLE = stringPreferencesKey("widget_sc_4_sub")
    val SHORTCUT_4_ART = stringPreferencesKey("widget_sc_4_art")
    val SHORTCUT_4_TYPE = stringPreferencesKey("widget_sc_4_type")
    val SHORTCUT_4_ID = stringPreferencesKey("widget_sc_4_id")

    val SHORTCUT_5_TITLE = stringPreferencesKey("widget_sc_5_title")
    val SHORTCUT_5_SUBTITLE = stringPreferencesKey("widget_sc_5_sub")
    val SHORTCUT_5_ART = stringPreferencesKey("widget_sc_5_art")
    val SHORTCUT_5_TYPE = stringPreferencesKey("widget_sc_5_type")
    val SHORTCUT_5_ID = stringPreferencesKey("widget_sc_5_id")

    val SHORTCUT_6_TITLE = stringPreferencesKey("widget_sc_6_title")
    val SHORTCUT_6_SUBTITLE = stringPreferencesKey("widget_sc_6_sub")
    val SHORTCUT_6_ART = stringPreferencesKey("widget_sc_6_art")
    val SHORTCUT_6_TYPE = stringPreferencesKey("widget_sc_6_type")
    val SHORTCUT_6_ID = stringPreferencesKey("widget_sc_6_id")

    val SHORTCUT_7_TITLE = stringPreferencesKey("widget_sc_7_title")
    val SHORTCUT_7_SUBTITLE = stringPreferencesKey("widget_sc_7_sub")
    val SHORTCUT_7_ART = stringPreferencesKey("widget_sc_7_art")
    val SHORTCUT_7_TYPE = stringPreferencesKey("widget_sc_7_type")
    val SHORTCUT_7_ID = stringPreferencesKey("widget_sc_7_id")

    val SHORTCUT_8_TITLE = stringPreferencesKey("widget_sc_8_title")
    val SHORTCUT_8_SUBTITLE = stringPreferencesKey("widget_sc_8_sub")
    val SHORTCUT_8_ART = stringPreferencesKey("widget_sc_8_art")
    val SHORTCUT_8_TYPE = stringPreferencesKey("widget_sc_8_type")
    val SHORTCUT_8_ID = stringPreferencesKey("widget_sc_8_id")

    // Widget 4 Queue list
    val QUEUE_ITEMS = stringPreferencesKey("widget_queue_items")
}
