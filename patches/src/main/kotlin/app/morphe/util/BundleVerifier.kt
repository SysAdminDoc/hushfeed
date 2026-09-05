package app.morphe.util

import app.morphe.patcher.patch.loadPatchesFromJar
import com.google.gson.JsonParser
import java.io.File
import java.util.jar.JarFile

/** Verifies the on-disk deliverable without running any task that can repair it. */
object BundleVerifier {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "Expected bundle, patch list and version" }
        val bundle = File(args[0])
        require(bundle.isFile) { "Bundle not found: $bundle" }
        JarFile(bundle).use { jar ->
            for (name in listOf("classes.dex", "extensions/tiktok.mpe", "extensions/shared.mpe")) {
                val entry = requireNotNull(jar.getJarEntry(name)) { "Bundle is missing $name" }
                require(entry.size > 0) { "Bundle entry is empty: $name" }
                jar.getInputStream(entry).use { stream ->
                    require(stream.readNBytes(4).contentEquals(byteArrayOf(100, 101, 120, 10))) {
                        "Bundle entry is not a DEX file: $name"
                    }
                }
            }
            require(jar.manifest.mainAttributes.getValue("Version") == args[2]) {
                "Bundle version does not match ${args[2]}"
            }
        }
        val metadata = File(args[1]).reader().use { JsonParser.parseReader(it).asJsonObject }
        require(metadata["version"].asString == "v${args[2]}") { "Patch list version is stale" }
        val listed = metadata.getAsJsonArray("patches").map { it.asJsonObject["name"].asString }
        val bundled = loadPatchesFromJar(setOf(bundle)).map { it.name }
        require(listed.isNotEmpty() && listed.size == listed.toSet().size) {
            "Patch list is empty or contains duplicate names"
        }
        require(bundled.size == listed.size && bundled.toSet() == listed.toSet()) {
            "Patch list mismatch: bundle has ${bundled.size}, metadata has ${listed.size}; " +
                "missing=${listed.toSet() - bundled.toSet()}, extra=${bundled.toSet() - listed.toSet()}"
        }
        println("Verified ${bundle.name}: ${bundled.size} patches and all three DEX payloads")
    }
}
