/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */

package app.morphe

import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingHeroTest {
    private val root = File("..").canonicalFile.takeIf { File(it, "README.md").isFile }
        ?: File(".").canonicalFile

    @Test
    fun `the selected hero is the first README content and appears once`() {
        val readme = File(root, "README.md").readText()
        val heroPath = "assets/readme-hero.png"
        val firstContent = readme.lineSequence().first { it.isNotBlank() }

        assertTrue("the README must lead with the marketing hero", firstContent.contains(heroPath))
        assertEquals("the selected hero must not be inserted twice", 1, readme.windowed(heroPath.length).count { it == heroPath })
        assertFalse("the retired header must stay out of the README", readme.contains("assets/readme-header.png"))
    }

    @Test
    fun `the README keeps a canonical Morphe source link and manual fallback`() {
        val readme = File(root, "README.md").readText()
        val canonicalLink = "https://morphe.software/add-source?github=SysAdminDoc%2Fhushfeed"
        val addSourceLinks = Regex("""https://morphe\.software/add-source\?github=[^)\s]+""")
            .findAll(readme)
            .map { it.value }
            .toList()

        assertTrue("the README must include an add-source link", addSourceLinks.isNotEmpty())
        assertTrue(
            "every add-source link must use the encoded repository slug",
            addSourceLinks.all { it == canonicalLink },
        )
        assertTrue(
            "the README must explain the manual Manager fallback",
            readme.contains("paste `https://github.com/SysAdminDoc/hushfeed`"),
        )
    }

    /** 7fcbb946 took the button out along with this test; both are back, and the button sits above the title. */
    @Test
    fun `the README keeps the canonical Ko-fi support link`() {
        val readme = File(root, "README.md").readText()
        val link = readme.indexOf("https://ko-fi.com/X8K126YVER")

        assertTrue("the README must keep the project support link", link >= 0)
        assertTrue("the support button belongs above the title", link < readme.indexOf("\n# Hushfeed"))
    }

    @Test
    fun `the hero keeps its review size and version-free source`() {
        val hero = ImageIO.read(File(root, "assets/readme-hero.png"))
        assertEquals(1600, hero.width)
        assertEquals(900, hero.height)

        val generator = File(root, "concepts/marketing/2026-09-12/generate_hero.py").readText()
        assertFalse(
            "release numbers make hero artwork stale",
            Regex("""\bv?\d+\.\d+\.\d+\b""").containsMatchIn(generator),
        )
    }

    @Test
    fun `the approved H and selected files stay byte exact`() {
        val approved = digest(File(root, "patches-bundle.png"))
        assertArrayEquals(approved, digest(File(root, "concepts/marketing/2026-09-12/source/user-approved-logo.png")))
        assertArrayEquals(approved, digest(File(root, "concepts/marketing/2026-09-12/selected/logo-h.png")))
        assertArrayEquals(
            digest(File(root, "assets/readme-hero.png")),
            digest(File(root, "concepts/marketing/2026-09-12/selected/hero-final.png")),
        )
    }

    private fun digest(file: File): ByteArray {
        assertTrue("missing reviewed marketing file: $file", file.isFile)
        return MessageDigest.getInstance("SHA-256").digest(file.readBytes())
    }
}
