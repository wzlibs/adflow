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

// Xuất :adflow-admob ra cùng local Maven repo với :adflow-core (xem adflow-core/build.gradle.kts) -
// dependency api(project(":adflow-core")) ở trên sẽ tự map sang coordinate "com.adflow:core:<version>"
// trong POM được publish, vì :adflow-core cũng đã đăng ký publication cùng groupId/version.
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
    repositories {
        maven {
            name = "LocalFlutterRepo"
            url = uri("$rootDir/flutter/adflow_flutter/android/local-maven")
        }
    }
}
