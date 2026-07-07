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
        // Local Maven repo tĩnh chứa :adflow-core/:adflow-admob đã publish (xem
        // adflow-core/build.gradle.kts + adflow-admob/build.gradle.kts ở repo gốc). Dùng đường dẫn
        // tương đối theo projectDir để vẫn đúng khi app Flutter khác include module này làm
        // subproject (Flutter Gradle plugin loader set projectDir trỏ thẳng vào thư mục android/ này).
        maven { url = uri("$projectDir/local-maven") }
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
    // Version phải khớp với `adflowVersion` trong gradle.properties ở repo gốc - đây là 2 Gradle
    // build độc lập nên không tự share property được; nhớ bump tay + publish lại mỗi khi core/admob
    // đổi API (xem README của plugin, phần "Rủi ro/giới hạn").
    api("com.adflow:core:0.1.0")
    api("com.adflow:admob:0.1.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.mockito:mockito-core:5.0.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
