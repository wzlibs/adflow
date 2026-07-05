plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.adflow.core"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
