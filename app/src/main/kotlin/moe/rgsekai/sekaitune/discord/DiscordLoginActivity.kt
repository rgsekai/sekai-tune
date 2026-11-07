/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors
import moe.rgsekai.sekaitune.MainActivity
import moe.rgsekai.sekaitune.di.DiscordEntryPoint

class DiscordLoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.data?.let { redirect ->
            EntryPointAccessors.fromApplication(applicationContext, DiscordEntryPoint::class.java)
                .discordAuth()
                .handleRedirect(redirect)
        }
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}

