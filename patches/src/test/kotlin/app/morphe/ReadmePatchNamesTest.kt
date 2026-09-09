package app.morphe

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The patch table in the README against the names the patches actually carry.
 *
 * <p>A patch is chosen by name in Morphe Manager, so the README table is the only place a
 * reader can look up what a name does before applying it. Nine patches were renamed in 0.26.0
 * and two of the rows kept the old name, which points a reader at something no longer in the
 * list. Nothing else in this repository reads the README, so nothing else noticed.
 */
class ReadmePatchNamesTest {
    private val patchFactories = listOf("bytecodePatch(", "resourcePatch(", "rawResourcePatch(")

    /** Every name a patch declares, whatever comments sit between the factory and the name. */
    private fun declaredPatchNames(): Set<String> {
        val names = mutableSetOf<String>()
        val sources = File("src/main/kotlin").takeIf { it.isDirectory }
            ?: File("patches/src/main/kotlin")
        assertTrue("could not find the patch sources from ${File(".").absolutePath}",
            sources.isDirectory)
        sources.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (patchFactories.none { line.contains(it) }) return@forEachIndexed
                for (ahead in index + 1 until minOf(index + 12, lines.size)) {
                    val text = lines[ahead].trim()
                    if (text.isEmpty() || text.startsWith("//")) continue
                    NAME.find(text)?.let { names.add(it.groupValues[1]) }
                    break
                }
            }
        }
        return names
    }

    /** Every name the README's patch table lists, which is the first cell of each row. */
    private fun readmePatchNames(): Set<String> {
        val readme = File("../README.md").takeIf { it.isFile } ?: File("README.md")
        assertTrue("could not find the README from ${File(".").absolutePath}", readme.isFile)
        return readme.readLines().mapNotNull { ROW.find(it)?.groupValues?.get(1) }.toSet()
    }

    @Test
    fun `the README patch table and the patch names say the same thing`() {
        val declared = declaredPatchNames()
        val listed = readmePatchNames()
        assertTrue("the scan found no patches", declared.size > 50)

        assertEquals(
            "the README lists a patch by a name no patch carries, so a reader cannot find it " +
                "in Morphe Manager",
            emptyList<String>(),
            (listed - declared).sorted(),
        )
        assertEquals(
            "a patch ships with no row in the README table",
            emptyList<String>(),
            (declared - listed).sorted(),
        )
    }

    private companion object {
        val NAME = Regex("""^name\s*=\s*"([^"]+)"""")

        // The table rows are the only lines that open with a pipe and a backticked name. The
        // credits further down name patches in prose, which is not a claim about the table.
        val ROW = Regex("""^\|\s*`([^`]+)`\s*\|""")
    }
}
