/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.library

import kotlinx.coroutines.flow.Flow
import moe.rgsekai.sekaitune.repository.LibraryTopMixRepository
import javax.inject.Inject

class ObserveLibraryTopMixesUseCase
    @Inject
    constructor(
        private val repository: LibraryTopMixRepository,
    ) {
        operator fun invoke(): Flow<List<LibraryTopMix>> = repository.observePersistedTopMixes()
    }




