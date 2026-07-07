plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.process)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// Xuất :adflow-core ra 1 local Maven repo tĩnh, checked into git, để android/build.gradle.kts của
// Flutter plugin (flutter/adflow_flutter) phụ thuộc được qua Maven coordinate bình thường - plugin
// Flutter khi được app khác tiêu thụ từ ngoài repo này không thể dùng project(":adflow-core").
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.adflow"
            artifactId = "core"
            version = project.findProperty("adflowVersion") as String? ?: "0.1.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            name = "LocalFlutterRepo"
            url = uri("$rootDir/flutter/adflow_flutter/android/local-maven")
        }
    }
}
