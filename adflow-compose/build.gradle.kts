plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.adflow.compose"
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":adflow-core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
}

// MavenPublication này cung cấp task publishToMavenLocal mà JitPack chạy khi build theo git tag
// (xem RELEASING.md) - groupId/version ở đây chỉ có ý nghĩa nội bộ cho publishToMavenLocal, JitPack
// tự phát coordinate công khai (com.github.wzlibs.adflow:compose:<tag>) dựa theo tag được request.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.adflow"
            artifactId = "compose"
            version = project.findProperty("adflowVersion") as String? ?: "0.1.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
