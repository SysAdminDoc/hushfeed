/**
 * Which capture each tracked picture in assets/settings comes from.
 *
 * <p>The suite writes about ninety images under the capture directory; twenty-eight of them are
 * published. The names do not line up on their own: the light twins are a `-light` suffix here
 * and a `pages/light` directory there, the dialogs are their own tree, Feed navigation publishes
 * the scrolled-to-the-end capture, and the gate recorder is at the top level with a hyphen where
 * the published name has an underscore. Every pair below was checked byte for byte against a
 * capture of an unchanged tree, which is what makes this a mapping rather than a guess.
 */
val trackedScreenshots = mapOf(
    "behavior.png" to "pages/dark/behavior.png",
    "comments-german-large.png" to "pages/dark/comments-german-large.png",
    "comments.png" to "pages/dark/comments.png",
    "creator-list.png" to "pages/dark/creator-list.png",
    "diagnostics-light.png" to "pages/light/diagnostics.png",
    "diagnostics.png" to "pages/dark/diagnostics.png",
    "dialog-multi-dark.png" to "dialogs/dark/multi-choice.png",
    "dialog-multi-light.png" to "dialogs/light/multi-choice.png",
    "dialog-single-dark.png" to "dialogs/dark/single-choice.png",
    "dialog-single-light.png" to "dialogs/light/single-choice.png",
    "downloads.png" to "pages/dark/downloads.png",
    "feed_filter.png" to "pages/dark/feed_filter.png",
    "feed_navigation.png" to "pages/dark/feed_navigation-end.png",
    "gate_details.png" to "pages/dark/gate_details.png",
    "gate_recording.png" to "gate-recording.png",
    "inbox.png" to "pages/dark/inbox.png",
    "interface.png" to "pages/dark/interface.png",
    "lab.png" to "pages/dark/lab.png",
    "playback-light.png" to "pages/light/playback.png",
    "playback.png" to "pages/dark/playback.png",
    "region.png" to "pages/dark/region.png",
    "rtl-large-light.png" to "pages/light/rtl-large.png",
    "rtl-large.png" to "pages/dark/rtl-large.png",
    "search.png" to "pages/dark/search.png",
    "settings.png" to "pages/dark/settings.png",
    "share.png" to "pages/dark/share.png",
    "two-times-text-light.png" to "pages/light/two-times-text.png",
    "two-times-text.png" to "pages/dark/two-times-text.png",
)

/** Read at configuration time so the test task can ask for the capture it is about to copy. */
val refreshingScreenshots = gradle.startParameter.taskNames.any {
    it == "refreshScreenshots" || it.endsWith(":refreshScreenshots")
}

dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:tiktok:stub"))
    compileOnly(libs.annotation)
    testImplementation(project(":extensions:shared:library"))
    testImplementation(project(":extensions:tiktok:stub"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

extension {
    name = "extensions/tiktok.mpe"
}

android {
    namespace = "app.morphe.extension.tiktok"

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // The payload is injected into TikTok, whose own floor is API 23. javac compiles against a
    // modern JDK and Robolectric runs on one, so a library call or a type above that floor is
    // green all the way to a phone, where D8 has left it as a stub that throws. NewApi is the
    // only check here that reads the SDK_INT guards instead of flagging every guarded call.
    lint {
        checkOnly += "NewApi"
        error += "NewApi"
        // NewApi catches a call above the floor. ObsoleteSdkInt catches an SDK_INT guard at or
        // below it, which is dead code today and a wrong floor the next time minSdk moves.
        checkOnly += "ObsoleteSdkInt"
        error += "ObsoleteSdkInt"
        abortOnError = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1g"
            // The translation tables are read from disk at run time, by the test that compares
            // them against the generated table. Without this Gradle sees no input change when a
            // table is edited, calls the task up to date, and the comparison never runs.
            it.inputs.dir(layout.projectDirectory.dir("src/main/l10n"))
                .withPropertyName("l10nTables")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            // refreshScreenshots asks for the capture itself, so it does not need the property
            // and cannot be pointed at assets/ by accident. Forcing the rerun matters because a
            // capture is not one of the task's declared outputs: an up to date test task writes
            // no screenshots at all, and the copy below would then compare against whatever was
            // left in build/ from a previous run.
            val captureInto = providers.gradleProperty("screenshotDir").orNull
                ?: if (refreshingScreenshots) "build/screenshots" else null
            if (captureInto != null) {
                it.systemProperty("morphe.screenshotDir", captureInto)
            }
            if (refreshingScreenshots) it.outputs.upToDateWhen { false }
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            )
        }
    }
}

/**
 * Re-captures the settings screenshots and copies back only the published ones that changed.
 *
 * <p>`./gradlew :extensions:tiktok:refreshScreenshots`. The capture goes to this module's build
 * directory, never to assets/: pointing the screenshotDir property there dropped about ninety
 * untracked files into the repository twice, on 2026-09-06 and again on 2026-09-08. What lands
 * in assets/ is the fixed list above and nothing else, and only where the bytes moved, so the
 * printed names are exactly the screens a change altered.
 */
tasks.register("refreshScreenshots") {
    group = "documentation"
    description = "Re-captures the settings screenshots and updates the ones in assets/settings."
    dependsOn("testDebugUnitTest")

    val captureDirectory = layout.buildDirectory.dir("screenshots")
    val assetsDirectory = rootProject.layout.projectDirectory.dir("assets/settings")
    val tracked = trackedScreenshots

    doLast {
        val capture = captureDirectory.get().asFile
        if (!capture.isDirectory) {
            throw GradleException(
                "No screenshots were captured in $capture. The suite writes them only when " +
                    "morphe.screenshotDir is set, which this task does for itself."
            )
        }

        val copied = mutableListOf<String>()
        val absent = mutableListOf<String>()
        for ((published, source) in tracked.toSortedMap()) {
            val from = capture.resolve(source)
            if (!from.isFile) {
                absent += "$published (no $source)"
                continue
            }
            val to = assetsDirectory.file(published).asFile
            if (to.isFile && from.readBytes().contentEquals(to.readBytes())) continue
            from.copyTo(to, overwrite = true)
            copied += published
        }

        if (absent.isNotEmpty()) {
            // A published picture whose capture has gone is a rename nobody carried through to
            // the map, and silently leaving the old file in place is how it stays wrong.
            throw GradleException(
                "These published screenshots have no capture any more, so the map above is out " +
                    "of date: " + absent.joinToString(", ")
            )
        }
        if (copied.isEmpty()) {
            logger.lifecycle("Screenshots: nothing moved.")
        } else {
            logger.lifecycle("Screenshots updated (${copied.size}): " + copied.joinToString(", "))
        }
    }
}
