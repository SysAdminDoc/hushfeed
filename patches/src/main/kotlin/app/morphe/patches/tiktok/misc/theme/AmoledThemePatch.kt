package app.morphe.patches.tiktok.misc.theme

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.colorOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import org.w3c.dom.Element

@Suppress("unused")
val amoledThemePatch = resourcePatch(
    name = "AMOLED dark theme",
    description = "Replaces TikTok's dark background palette with black or a chosen color. The light theme keeps its colors. Choose this patch to enable it. It is the one patch that rewrites resources, so patching with it on needs the memory limit raised to 768 MB.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    val background by colorOption(
        key = "backgroundColor",
        default = "#000000",
        title = "Dark background color",
        description = "An opaque hex color. Black turns off OLED pixels on the main dark surfaces.",
        values = mapOf("Black" to "#000000", "Mocha" to "#181825", "Dark gray" to "#121212"),
    )
    execute {
        val color = background ?: throw PatchException("Choose a background color")
        if (!Regex("#[0-9a-fA-F]{6}|#[fF]{2}[0-9a-fA-F]{6}").matches(color)) {
            throw PatchException("Background color must be opaque #RRGGBB or #FFRRGGBB")
        }
        // Verified dark-only palette in 46.2.3. Window backgrounds already resolve
        // attr/fx_ to a40; the light styles use their separate light palette.
        val backgrounds = setOf("a3y", "a40", "a41", "a43")
        val found = mutableSetOf<String>()
        get("res").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { it.resolve("colors.xml") }.filter { it.exists() }.forEach { file ->
                document(file.relativeTo(get(".")).invariantSeparatorsPath).use { xml ->
                    val colors = xml.getElementsByTagName("color")
                    for (index in 0 until colors.length) {
                        val entry = colors.item(index) as Element
                        val name = entry.getAttribute("name")
                        if (name in backgrounds) {
                            entry.textContent = color
                            found.add(name)
                        }
                    }
                }
            }
        if (found != backgrounds) throw PatchException("Dark background palette is incomplete: $found")
    }
}
