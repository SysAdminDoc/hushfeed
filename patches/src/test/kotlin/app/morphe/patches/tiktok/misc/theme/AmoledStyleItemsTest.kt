package app.morphe.patches.tiktok.misc.theme

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import app.morphe.patcher.patch.PatchException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/** The sheet fills live in style literals, not in colors.xml, and only the dark ones move. */
class AmoledStyleItemsTest {
    @Test
    fun `dark sheet literals take the colour and the light and translucent ones stay`() {
        val styles = parse(
            """
            <resources>
              <style name="a9z"><item name="agk">#ff282828</item><item name="c3">#FF252525</item></style>
              <style name="zb"><item name="agk">#ff1e1e1e</item><item name="c3">#ffffffff</item></style>
              <style name="z6"><item name="dy">#1effffff</item><item name="agk">?attr/aut</item></style>
              <style name="a_0"><item name="agk">#1d2026</item></style>
              <style name="zf"><item name="aia">#ff1e1e1e</item><item name="axi">#ffffffff</item></style>
            </resources>
            """.trimIndent(),
        )

        val changed = rewriteDarkStyleItems(styles, SHEET_STYLE_ITEMS, "#000000")

        assertEquals(setOf("agk", "c3", "aia"), changed)
        assertEquals(
            listOf("#000000", "#000000", "#000000", "#ffffffff", "#1effffff", "?attr/aut", "#000000", "#000000", "#ffffffff"),
            values(styles),
        )
    }

    @Test
    fun `nothing dark to rewrite is reported as nothing changed`() {
        val styles = parse("""<resources><style name="zb"><item name="c3">#ffffffff</item></style></resources>""")
        assertTrue(rewriteDarkStyleItems(styles, SHEET_STYLE_ITEMS, "#000000").isEmpty())
    }

    @Test
    fun `a declared build needs every sheet item and a forced build needs one`() {
        assertEquals(setOf("47.0.3"), declaredVersions())
        checkSheetStyleItems(setOf("agk", "c3", "aia"), "47.0.3", setOf("47.0.3"))
        assertRefused { checkSheetStyleItems(setOf("agk", "c3"), "47.0.3", setOf("47.0.3")) }
        checkSheetStyleItems(setOf("agk"), "46.9.3", setOf("47.0.3"))
        checkSheetStyleItems(setOf("agk"), null, setOf("47.0.3"))
        assertRefused { checkSheetStyleItems(setOf("agk"), "47.0.3", setOf("47.0.3")) }
        assertRefused { checkSheetStyleItems(emptySet(), "46.9.3", setOf("47.0.3")) }
    }

    /** The review of a02d0d67: on 46.7.3 to 46.9.3 aia is UISheetGrouped3's dark value, another tier. */
    @Test
    fun `aia is rewritten only on a declared build`() {
        assertEquals(SHEET_STYLE_ITEMS, sheetStyleItems("47.0.3", setOf("47.0.3")))
        assertEquals(setOf("agk", "c3"), sheetStyleItems("46.9.3", setOf("47.0.3")))
        assertEquals(setOf("agk", "c3"), sheetStyleItems(null, setOf("47.0.3")))
    }

    /** 47.0.3 moved every gray one name along; the 46.x names there are an accent and overlays. */
    @Test
    fun `the palette is each build's own and a build never read is refused`() {
        assertEquals(setOf("a3z", "a41", "a42", "a44", "a4b"), darkBackgroundColors("47.0.3"))
        listOf("46.2.3", "46.7.3", "46.8.3", "46.9.3").forEach { version ->
            assertEquals(version, setOf("a3y", "a40", "a41", "a43", "a4a"), darkBackgroundColors(version))
        }
        declaredVersions().forEach { assertTrue("no palette for declared $it", darkBackgroundColors(it).size == 5) }
        assertRefused { darkBackgroundColors("47.1.2") }
        assertRefused { darkBackgroundColors(null) }
        val refusal = unreadPaletteRefusal("47.1.2")
        assertTrue(refusal, refusal.contains("TikTok 47.1.2") && refusal.contains("nothing was changed"))
        assertTrue(refusal, refusal.endsWith("46.2.3, 46.7.3, 46.8.3, 46.9.3 and 47.0.3."))
        assertTrue(unreadPaletteRefusal(null).contains("this TikTok build"))
    }

    private fun assertRefused(check: () -> Unit) {
        try {
            check()
        } catch (refusal: PatchException) {
            return
        }
        fail("the check accepted what it should have refused")
    }

    @Test
    fun `a dark opaque literal is six or eight hex digits with every channel under 40`() {
        assertTrue(isDarkOpaqueLiteral("#282828"))
        assertTrue(isDarkOpaqueLiteral("#FF161823"))
        assertTrue(isDarkOpaqueLiteral(" #ff3f3f3f "))
        assertFalse(isDarkOpaqueLiteral("#404040"))
        assertFalse(isDarkOpaqueLiteral("#80000000"))
        assertFalse(isDarkOpaqueLiteral("#fff"))
        assertFalse(isDarkOpaqueLiteral("@color/a40"))
        assertFalse(isDarkOpaqueLiteral("?attr/agk"))
    }

    private fun parse(xml: String): Document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray()))

    private fun values(styles: Document): List<String> {
        val items = styles.getElementsByTagName("item")
        return (0 until items.length).map { (items.item(it) as Element).textContent }
    }
}
