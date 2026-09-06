package app.morphe.patches.tiktok.misc.settings

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Adds the settings text for every bundled language to TikTok's resources.
 *
 * The bundle carries l10n/index.txt naming the resource folders (values, values-de, ...)
 * and a strings.xml under each, generated from extensions/tiktok/src/main/l10n by
 * scripts/gen-l10n.py. Each string is named by the CRC32 of its English text, which is
 * what the extension's L10n class asks the app's resources for at runtime. Strings TikTok
 * already has under the same name are left alone.
 */
internal val addResourcesPatch = resourcePatch {
    execute {
        val loader = object {}.javaClass.classLoader
        val folders = loader.getResourceAsStream("l10n/index.txt")
            ?.bufferedReader()?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: throw PatchException("The bundle has no settings text: l10n/index.txt is missing")

        folders.forEach { folder ->
            val bundled = loader.getResourceAsStream("l10n/$folder/strings.xml")
                ?: throw PatchException("The bundle has no l10n/$folder/strings.xml")
            val path = "res/$folder/strings.xml"
            val target = get(path)
            if (!target.exists()) {
                target.parentFile.mkdirs()
                target.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n")
            }
            document(bundled).use { source ->
                document(path).use { xml ->
                    val root = xml.documentElement
                    val present = mutableSetOf<String>()
                    val existing = root.getElementsByTagName("string")
                    for (index in 0 until existing.length) {
                        present += (existing.item(index) as Element).getAttribute("name")
                    }
                    val strings = source.documentElement.getElementsByTagName("string")
                    var added = 0
                    for (index in 0 until strings.length) {
                        val string = strings.item(index) as Element
                        if (string.getAttribute("name") in present) continue
                        root.appendChild(xml.importNode(string, true))
                        added++
                    }
                    if (added == 0 && strings.length > 0 && present.none { it.startsWith("mtp_") }) {
                        throw PatchException("No settings text was added to $path")
                    }
                }
            }
        }
    }
}
