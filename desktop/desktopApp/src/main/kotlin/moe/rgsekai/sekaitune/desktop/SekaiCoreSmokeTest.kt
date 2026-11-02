package moe.rgsekai.sekaitune.desktop

import kotlinx.coroutines.runBlocking
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.innertube.models.YTItem

fun main() {
    println("=== Sekai Tune :core YouTube Search Smoke Test ===")
    val query = "arijit singh"
    println("Searching for: \"$query\" using moe.rgsekai.sekaitune.innertube.YouTube.searchSummary...")

    runBlocking {
        val summaryResult = YouTube.searchSummary(query)
        summaryResult.onSuccess { summaryPage ->
            println("\n[SUCCESS] Retrieved search results via YouTube.searchSummary:")
            val songItems = mutableListOf<YTItem>()

            // Extract items from summaries / sections
            summaryPage.summaries.forEach { section ->
                section.items.forEach { item ->
                    if (songItems.size < 5) {
                        songItems.add(item)
                    }
                }
            }

            // If summary sections had fewer than 5 items, fallback to YouTube.search with SONGS filter
            if (songItems.isEmpty()) {
                println("Summary page had no items, falling back to YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)...")
                val filterResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                filterResult.onSuccess { searchResult ->
                    searchResult.items.take(5).forEach { songItems.add(it) }
                }.onFailure { err ->
                    println("YouTube.search failed: ${err.message}")
                }
            }

            println("\nFirst ${songItems.take(5).size} results:")
            songItems.take(5).forEachIndexed { index, item ->
                when (item) {
                    is SongItem -> {
                        val artists = item.artists.joinToString(", ") { it.name }
                        println("${index + 1}. Title: ${item.title} | Artist: $artists | Video ID: ${item.id}")
                    }
                    else -> {
                        println("${index + 1}. Title: ${item.title} | ID: ${item.id} (${item::class.simpleName})")
                    }
                }
            }
        }.onFailure { error ->
            println("[FAILURE] YouTube.searchSummary failed with error: ${error.message}")
            error.printStackTrace()
        }
    }
    println("\n=== Smoke Test Complete ===")
}
