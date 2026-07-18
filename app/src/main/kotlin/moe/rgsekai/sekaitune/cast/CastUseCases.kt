/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.cast

class ObserveCastStateUseCase(
    private val repository: CastPlaybackRepository,
) {
    operator fun invoke() = repository.screenState
}

class SelectCastRouteUseCase

class DisconnectCastSessionUseCase(
    private val repository: CastPlaybackRepository,
) {
    operator fun invoke() = repository.disconnect()
}

class SetCastVolumeUseCase(
    private val repository: CastPlaybackRepository,
) {
    operator fun invoke(volume: Float) = repository.setVolume(volume)
}




