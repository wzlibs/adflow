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
        // Robolectric cần resources thật để inflate AdFlowBannerView/AdFlowNativeAdView với
        // custom attrs (app:adflowPlacementId) trong test.
        unitTests.isIncludeAndroidResources = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // api (không phải implementation): StateFlow<AdState> nằm trong API public của mọi
    // controller/placement - client cần thấy kotlinx.coroutines trên compile classpath.
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.process)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}

// MavenPublication này cung cấp task publishToMavenLocal mà JitPack chạy khi build theo git tag
// (xem RELEASING.md) - groupId/version ở đây chỉ có ý nghĩa nội bộ cho publishToMavenLocal, JitPack
// tự phát coordinate công khai (com.github.wzlibs.adflow:core:<tag>) dựa theo tag được request, bất
// kể giá trị groupId/version khai báo ở đây.
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
}
