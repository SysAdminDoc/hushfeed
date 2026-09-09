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
            providers.gradleProperty("screenshotDir").orNull?.let { directory ->
                it.systemProperty("morphe.screenshotDir", directory)
            }
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
