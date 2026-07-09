group = "com.adflow.adflow_flutter"
version = "1.0-SNAPSHOT"

buildscript {
    val kotlinVersion = "2.3.20"
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.0.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // adflow-core/adflow-admob được publish qua JitPack (xem RELEASING.md ở root repo) -
        // groupId khi build qua JitPack luôn là com.github.<owner>.<repo>, khác groupId nội bộ
        // "com.adflow" cấu hình trong build.gradle.kts của 2 module đó.
        maven("https://jitpack.io")
    }
}

plugins {
    id("com.android.library")
}

android {
    namespace = "com.adflow.adflow_flutter"

    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        minSdk = 24
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()

                it.outputs.upToDateWhen { false }

                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Tag JitPack (không phải adflowVersion trong gradle.properties ở repo gốc) - bump khi
    // adflow-core/adflow-admob có tag mới (xem RELEASING.md ở root repo).
    api("com.github.wzlibs.adflow:core:v0.6.0")
    api("com.github.wzlibs.adflow:admob:v0.6.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.mockito:mockito-core:5.0.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
