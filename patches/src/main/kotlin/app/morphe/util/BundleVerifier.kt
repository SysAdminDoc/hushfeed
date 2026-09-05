package app.morphe.util

import app.morphe.patcher.patch.loadPatchesFromJar
import com.google.gson.JsonParser
import java.io.File
import java.util.jar.JarFile
import java.security.MessageDigest

/** Verifies the on-disk deliverable without running any task that can repair it. */
object BundleVerifier {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4) { "Expected bundle, patch list, version and build checksum" }
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
        val expectedDigest = File(args[3]).readText().trim()
        val actualDigest = MessageDigest.getInstance("SHA-256").digest(bundle.readBytes())
            .joinToString("") { "%02x".format(it) }
        require(actualDigest == expectedDigest) {
            "Bundle differs from the Android build output; a DEX or JVM payload may be stale"
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
