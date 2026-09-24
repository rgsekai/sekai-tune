/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.widget

import javax.inject.Inject

internal class LoadWidgetShortcutsUseCase
    @Inject
    constructor(
        private val repository: WidgetShortcutsRepository,
    ) {
        suspend operator fun invoke(
            queueItems: List<WidgetQueueItem> = emptyList(),
            nowMs: Long = System.currentTimeMillis(),
        ): WidgetShortcutsSnapshot {
            val shortcuts = repository.loadShortcuts(nowMs)
            return WidgetShortcutsSnapshot(
                shortcut1 = shortcuts.getOrElse(0) { WidgetShortcutItem.Empty },
                shortcut2 = shortcuts.getOrElse(1) { WidgetShortcutItem.Empty },
                shortcut3 = shortcuts.getOrElse(2) { WidgetShortcutItem.Empty },
                shortcut4 = shortcuts.getOrElse(3) { WidgetShortcutItem.Empty },
                shortcut5 = shortcuts.getOrElse(4) { WidgetShortcutItem.Empty },
                shortcut6 = shortcuts.getOrElse(5) { WidgetShortcutItem.Empty },
                shortcut7 = shortcuts.getOrElse(6) { WidgetShortcutItem.Empty },
                shortcut8 = shortcuts.getOrElse(7) { WidgetShortcutItem.Empty },
                queueItems = queueItems,
            )
        }
    }
