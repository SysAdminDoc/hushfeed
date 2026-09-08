import org.gradle.api.artifacts.repositories.MavenArtifactRepository

rootProject.name = "hushfeed"

pluginManagement {
    val allowLocalPlugins = providers.gradleProperty("allowMavenLocal")
        .map(String::toBoolean)
        .getOrElse(false)
    repositories {
        // Local plugin artifacts are useful while developing the plugin, but they must never
        // silently override the reviewed repositories used for a release build.
        if (allowLocalPlugins) mavenLocal()
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GITHUB_ACTOR"))
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN"))
            }
        }
        // baksmali and smali come from a source build, resolved as com.github.MorpheApp.smali
        // rather than from Maven Central. The comment used to name iBotPeaches/smali, which the
        // build stopped using when the patches plugin switched forks at 1.3.2.
        //
        // google/smali 3.0.10 shipped officially on 2026-08-20, so "until official releases come
        // out again" is no longer the reason to keep this. The reason is that the pin has to be
        // whatever the patcher was built against: two copies of dexlib2 on the R8 classpath fail
        // buildAndroid with "Classpath type already present".
        maven { url = uri("https://jitpack.io") }
    }
}

val allowMavenLocal = providers.gradleProperty("allowMavenLocal")
    .map(String::toBoolean)
    .getOrElse(false)

plugins {
    id("app.morphe.patches") version "1.3.4"
}

settings {
    extensions {
        defaultNamespace = "app.morphe.extension"

        // Must resolve to an absolute path (not relative),
        // otherwise the extensions in subfolders will fail to find the proguard config.
        proguardFiles(rootProject.projectDir.resolve("extensions/proguard-rules.pro").toString())
    }
}

if (!allowMavenLocal) {
    val localMavenUrl = file(System.getProperty("user.home"))
        .resolve(".m2/repository")
        .toURI()
        .toString()
        .removeSuffix("/")
    val localRepositories = dependencyResolutionManagement.repositories.filter { repository ->
        repository is MavenArtifactRepository
                && repository.url.toString().removeSuffix("/") == localMavenUrl
    }
    localRepositories.forEach { repository ->
        dependencyResolutionManagement.repositories.remove(repository)
    }
}

include(":patches:stub")
