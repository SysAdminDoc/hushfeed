package app.morphe.patches.tiktok.misc.theme

import app.morphe.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which colors the AMOLED patch paints, held to each fixture's own dark palette.
 *
 * <p>The dark app themes set a block of color tokens, the attrs TikTok's views read for their
 * surfaces. On every build read so far the block opens with the same seven values, an 8% white
 * overlay, the grays #252525, #121212, #252525 and #2e2e2e, then 12% and 4% white overlays, and
 * the background grays are the block's opaque dark colors other than pure black. The styles that
 * set that run are the dark themes' blocks, the grays they point at have to be exactly the
 * build's entry in [DARK_BACKGROUND_COLORS], and no other style may point at one, since the patch
 * rewrites the color itself and a light theme sharing it would go dark too.
 */
class AmoledPaletteTest {
    @Test
    fun `each fixture's background grays are the ones its dark token block sets`() {
        val versions = mutableListOf<String>()
        for (apk in Fixtures.apks()) {
            val version = Regex("""\d+\.\d+\.\d+""").find(apk.name)?.value ?: error("no version in ${apk.name}")
            versions += version
            val table = FixtureResourceTable(apk)
            val blocks = table.styles.filter { style ->
                table.colorReferences(style).map { table.colorValues[it] }
                    .windowed(OPENING_RUN.size).any { it == OPENING_RUN }
            }
            assertTrue("$version: no style sets the dark token block", blocks.isNotEmpty())
            val grays = blocks.flatMap { table.colorReferences(it) }.filter { name ->
                table.colorValues[name]?.let { isDarkOpaqueLiteral(it) && it != BLACK } == true
            }.toSet()
            val palette = darkBackgroundColors(version)
            assertEquals("$version: the dark token block's grays", grays, palette)

            val blockNames = blocks.map { it.name }.toSet()
            val elsewhere = table.styles.filter { it.name !in blockNames && table.colorReferences(it).any(palette::contains) }
            assertTrue("$version: other styles point at the palette: ${elsewhere.map { it.name }}", elsewhere.isEmpty())

            if (version == "47.0.3") {
                // What the 46.x names hold here: an orange, the 8% overlay, the #121212 gray, the
                // 12% overlay and the brand red. The patch painted all five black before this.
                assertEquals(
                    listOf("#ffff4e33", "#14ffffff", "#ff121212", "#1fffffff", "#ffff3b5c"),
                    listOf("a3y", "a40", "a41", "a43", "a4a").map { table.colorValues[it] },
                )
            }
        }
        assertTrue("the declared build is not among the fixtures: $versions", declaredVersions().all { it in versions })
    }

    private companion object {
        val OPENING_RUN = listOf("#14ffffff", "#ff252525", "#ff121212", "#ff252525", "#ff2e2e2e", "#1fffffff", "#0affffff")
        const val BLACK = "#ff000000"
    }
}
