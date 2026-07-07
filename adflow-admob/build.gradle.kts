plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.adflow.admob"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":adflow-core"))
    implementation(libs.play.services.ads)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// MavenPublication này cung cấp task publishToMavenLocal mà JitPack chạy khi build theo git tag
// (xem RELEASING.md) - dependency api(project(":adflow-core")) tự map sang "com.adflow:core:<version>"
// trong POM (JitPack sẽ tự viết lại thành com.github.wzlibs.adflow:core:<tag> khi phục vụ ra ngoài).
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.adflow"
            artifactId = "admob"
            version = project.findProperty("adflowVersion") as String? ?: "0.1.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
