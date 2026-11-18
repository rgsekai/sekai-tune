package moe.rgsekai.sekaitune.car.screens

import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridSection
import androidx.car.app.model.Row
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.car.CarApp
import moe.rgsekai.sekaitune.car.CarCollection
import moe.rgsekai.sekaitune.car.CarSong

private fun String.orDash(): String = ifBlank { "—" }

internal fun songRow(song: CarSong, cover: CarIcon, onClick: () -> Unit): Row =
    Row.Builder()
        .setTitle(song.title.orDash())
        .apply { if (song.subtitle.isNotBlank()) addText(song.subtitle) }
        .setImage(cover, Row.IMAGE_TYPE_LARGE)
        .setOnClickListener(onClick)
        .build()

internal fun collectionRow(collection: CarCollection, cover: CarIcon, onClick: () -> Unit): Row =
    Row.Builder()
        .setTitle(collection.title.orDash())
        .apply { collection.subtitle?.takeIf { it.isNotBlank() }?.let { addText(it) } }
        .setImage(cover, Row.IMAGE_TYPE_LARGE)
        .setBrowsable(true)
        .setOnClickListener(onClick)
        .build()

internal fun songTile(song: CarSong, cover: CarIcon, onClick: () -> Unit): GridItem =
    GridItem.Builder()
        .setTitle(song.title.orDash())
        .apply { if (song.subtitle.isNotBlank()) setText(song.subtitle) }
        .setImage(cover, GridItem.IMAGE_TYPE_LARGE)
        .setOnClickListener(onClick)
        .build()

internal fun collectionTile(title: String, subtitle: String?, cover: CarIcon, onClick: () -> Unit): GridItem =
    GridItem.Builder()
        .setTitle(title.orDash())
        .apply { subtitle?.takeIf { it.isNotBlank() }?.let { setText(it) } }
        .setImage(cover, GridItem.IMAGE_TYPE_LARGE)
        .setOnClickListener(onClick)
        .build()

internal fun coverGrid(): GridSection.Builder = GridSection.Builder().setItemSize(GridSection.ITEM_SIZE_LARGE)

@UnstableApi
internal fun CarApp.playerButton(): Action =
    Action.Builder(Action.MEDIA_PLAYBACK).setOnClickListener { showNowPlaying() }.build()

