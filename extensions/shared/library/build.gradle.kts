plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.morphe.extension"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The payload is injected into TikTok, whose own floor is API 23. javac compiles against a
    // modern JDK and Robolectric runs on one, so a library call or a type above that floor is
    // green all the way to a phone, where D8 has left it as a stub that throws. NewApi is the
    // only check here that reads the SDK_INT guards instead of flagging every guarded call.
    lint {
        checkOnly += "NewApi"
        error += "NewApi"
        abortOnError = true
    }
}

dependencies {
    compileOnly(libs.annotation)
}
