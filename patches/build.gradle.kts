import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The moment the bundle says it was built.
 *
 * Two builds of one commit used to differ in exactly one field, the manifest's Timestamp, so a
 * third party could rebuild the bundle and never match the published hash: a checksum then
 * attests to one file rather than to the source it came from. This pins the field to
 * SOURCE_DATE_EPOCH when the environment sets one, otherwise to the commit being built,
 * otherwise to zero. Anything read from the clock would put the difference straight back.
 */
val sourceDateEpoch: Long = run {
    providers.environmentVariable("SOURCE_DATE_EPOCH").orNull?.trim()?.toLongOrNull()?.let {
        return@run it
    }
    try {
        providers.exec {
            commandLine("git", "log", "-1", "--format=%ct")
            workingDir = rootProject.projectDir
            isIgnoreExitValue = true
        }.standardOutput.asText.orNull?.trim()?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        // No git, or no repository. Zero is stable, which is the only property that matters.
        0L
    }
}

/**
 * Rewrites the bundle with the timestamp pinned, leaving everything else as it was.
 *
 * <p>Every entry is written again rather than copied compressed, because the zip API offers no
 * way to move compressed bytes across without decoding them. That makes the output differ from
 * what the plugin first wrote, which does not matter: what matters is that two runs of this
 * produce the same bytes, and they do, because nothing here reads a clock.
 */
fun pinBundleTimestamp(bundle: File, epochSeconds: Long) {
    val stampMillis = epochSeconds * 1000L
    val names = mutableListOf<String>()
    val contents = mutableMapOf<String, ByteArray>()
    val times = mutableMapOf<String, Long>()
    val methods = mutableMapOf<String, Int>()

    ZipFile(bundle).use { zip ->
        for (entry in zip.entries()) {
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            names += entry.name
            contents[entry.name] = bytes
            times[entry.name] = entry.time
            methods[entry.name] = entry.method
        }
    }

    val manifestName = "META-INF/MANIFEST.MF"
    val manifest = contents[manifestName]
        ?: throw GradleException("The bundle has no $manifestName: $bundle")
    val pinned = String(manifest, Charsets.UTF_8)
        .replace(Regex("(?m)^Timestamp: [0-9]+"), "Timestamp: $stampMillis")
    if (!pinned.contains("Timestamp: $stampMillis")) {
        throw GradleException("The bundle manifest has no Timestamp line to pin: $bundle")
    }
    contents[manifestName] = pinned.toByteArray(Charsets.UTF_8)

    val rebuilt = ByteArrayOutputStream()
    ZipOutputStream(rebuilt).use { out ->
        for (name in names) {
            val bytes = contents.getValue(name)
            val entry = ZipEntry(name)
            entry.time = times.getValue(name)
            entry.method = methods.getValue(name)
            if (entry.method == ZipEntry.STORED) {
                entry.size = bytes.size.toLong()
                entry.crc = CRC32().apply { update(bytes) }.value
            }
            out.putNextEntry(entry)
            out.write(bytes)
            out.closeEntry()
        }
    }
    bundle.writeBytes(rebuilt.toByteArray())
}

group = "app.morphe"

patches {
    about {
        name = "Hushfeed"
        description = "Hushfeed patches for TikTok 46.2.3, built for Morphe. Fewer accidental taps, less noise, more control over the feed, inbox, comments and downloads."
        source = "https://github.com/SysAdminDoc/hushfeed"
        author = "SysAdminDoc"
        contact = "https://github.com/SysAdminDoc/hushfeed/issues"
        website = "https://github.com/SysAdminDoc/hushfeed"
        license = "GNU General Public License v3.0, with additional GPL section 7 requirements"
    }
}

dependencies {
    compileOnly(libs.morphe.patcher)

    // Used by JsonGenerator.
    implementation(libs.gson)

    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))

    // The register and instruction helpers under app.morphe.util are the only thing tested
    // here. They take dexlib2 methods, so the patcher has to be on the test runtime classpath
    // as well as the compile one.
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.morphe.patcher)
}

tasks {
    // ReadmePatchNamesTest reads the README, which is not a source file of this module, so
    // nothing told Gradle the answer changes when it does. Without this the task stayed
    // up to date over a README edit and the gate passed a table it had never looked at.
    test {
        inputs.file(rootProject.file("README.md"))
            .withPropertyName("readme")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
    val verifyBundle = register<JavaExec>("verifyBundle") {
        group = "verification"
        description = "Check the Android bundle and its published patch list without rebuilding it"
        dependsOn(classes)
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.BundleVerifier")
        args(
            providers.gradleProperty("patchBundle").getOrElse(
                layout.buildDirectory.file("libs/patches-${project.version}.mpp").get().asFile.absolutePath
            ),
            rootProject.file("patches-list.json").absolutePath,
            project.version.toString(),
            layout.buildDirectory.file("bundle.sha256").get().asFile.absolutePath
        )
    }
    named("buildAndroid") {
        // Resolved at configuration time. Reaching for project inside doLast is what the
        // configuration cache refuses, and Gradle 10 turns that refusal into an error.
        val bundleFile = layout.buildDirectory.file("libs/patches-${project.version}.mpp")
        val checksumFile = layout.buildDirectory.file("bundle.sha256")
        val pinnedEpoch = sourceDateEpoch
        doLast {
            // Before the checksum, so what is recorded is what a rebuild will produce.
            pinBundleTimestamp(bundleFile.get().asFile, pinnedEpoch)
            // Record only at the producer boundary. Standalone verification must not
            // bless a modified bundle by generating its own expected checksum.
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(bundleFile.get().asFile.readBytes())
                .joinToString("") { "%02x".format(it) }
            checksumFile.get().asFile.writeText(digest)
        }
        finalizedBy(verifyBundle)
    }
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
        args(project.version.toString())
    }
    // The patch list has to be regenerated before anything publishes the bundle.
    publish {
        dependsOn("generatePatchesList")
    }
}
