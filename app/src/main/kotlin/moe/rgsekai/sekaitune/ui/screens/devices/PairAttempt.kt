package moe.rgsekai.sekaitune.ui.screens.devices

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.link.LinkPeer
import moe.rgsekai.sekaitune.link.phone.PairException
import moe.rgsekai.sekaitune.link.phone.PhoneLinkHost

// where a pairing attempt is, for the line under the code
sealed interface PairUi {
    data object Idle : PairUi
    data object Working : PairUi
    data object WrongCode : PairUi
    data object Full : PairUi
    data object Failed : PairUi
    data object Outdated : PairUi
    data object NoneFound : PairUi
    data class Done(val name: String) : PairUi
}

internal val PairUi.note: Int?
    get() = when (this) {
        PairUi.Working -> R.string.devices_looking
        PairUi.WrongCode -> R.string.devices_wrong_code
        PairUi.Full -> R.string.devices_pair_full
        PairUi.Failed -> R.string.devices_pair_failed
        PairUi.Outdated -> R.string.devices_pair_outdated
        PairUi.NoneFound -> R.string.devices_enter_code_none
        else -> null
    }

internal val PairUi.isProblem: Boolean
    get() = this == PairUi.WrongCode || this == PairUi.Full || this == PairUi.Failed || this == PairUi.Outdated || this == PairUi.NoneFound

@Stable
internal class PairAttempt(private val host: PhoneLinkHost) {
    var state by mutableStateOf<PairUi>(PairUi.Idle)
        private set

    fun reset() {
        state = PairUi.Idle
    }

    suspend fun pair(peers: List<LinkPeer>, code: String) {
        if (peers.isEmpty()) {
            state = PairUi.NoneFound
            return
        }
        state = PairUi.Working
        var wrong = false
        var full = false
        var outdated = false
        for (peer in peers) {
            val result = host.pair(peer, code)
            val paired = result.getOrNull()
            if (paired != null) {
                state = PairUi.Done(paired.name)
                return
            }
            when ((result.exceptionOrNull() as? PairException)?.reason) {
                PairException.Reason.WrongCode -> wrong = true
                PairException.Reason.Full -> full = true
                PairException.Reason.Outdated -> outdated = true
                else -> Unit
            }
        }
        state = when {
            full -> PairUi.Full
            wrong -> PairUi.WrongCode
            outdated -> PairUi.Outdated
            else -> PairUi.Failed
        }
    }
}

