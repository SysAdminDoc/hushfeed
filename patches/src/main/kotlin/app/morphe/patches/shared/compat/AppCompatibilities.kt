/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/shared/compat/AppCompatibilities.kt
 *
 * Central Morphe `Compatibility` metadata so Morphe Manager shows human-readable app names.
 */
package app.morphe.patches.shared.compat

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object AppCompatibilities {
    private const val TIKTOK_COLOR = 0xFE2C55

    /** Target: TikTok 46.2.3 global package. */
    fun tiktok4623(): Array<Compatibility> = arrayOf(
        Compatibility(
            name = "TikTok",
            packageName = "com.zhiliaoapp.musically",
            appIconColor = TIKTOK_COLOR,
            targets = listOf(AppTarget("46.2.3")),
        ),
    )
}
