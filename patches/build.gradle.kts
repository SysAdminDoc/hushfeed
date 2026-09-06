import java.security.MessageDigest

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
}

tasks {
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
        doLast {
            // Record only at the producer boundary. Standalone verification must not
            // bless a modified bundle by generating its own expected checksum.
            val bundle = layout.buildDirectory.file("libs/patches-${project.version}.mpp").get().asFile
            val digest = MessageDigest.getInstance("SHA-256").digest(bundle.readBytes())
                .joinToString("") { "%02x".format(it) }
            layout.buildDirectory.file("bundle.sha256").get().asFile.writeText(digest)
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
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
