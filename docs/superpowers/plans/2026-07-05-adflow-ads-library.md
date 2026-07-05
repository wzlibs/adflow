# AdFlow Ads Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the AdFlow ads library (`:adflow-core` contracts + `:adflow-admob` AdMob implementation) covering Interstitial, App Open, Rewarded, Native and Banner ads with waterfall loading, incremental retry, show-interval capping, pluggable policies/revenue tracking, and a `:app` demo exercising every ad type.

**Architecture:** `:adflow-core` holds network-agnostic contracts and orchestration logic (no ad SDK dependency); `:adflow-admob` implements those contracts against the real AdMob SDK; `:app` depends on both and is the only place that constructs concrete instances. See `docs/superpowers/specs/2026-07-05-adflow-ads-library-design.md` for the full design rationale.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1 (built-in Kotlin support — no separate `org.jetbrains.kotlin.android` plugin needed), Jetpack Compose (BOM `2026.02.01`), JUnit4, `com.google.android.gms:play-services-ads`.

## Global Constraints

- minSdk 24, compileSdk 37, targetSdk 36 — match `app/build.gradle.kts` (compileSdk was bumped from 36 to 37 before Task 1 to fix a pre-existing `androidx.core:core-ktx:1.19.0` AAR metadata baseline failure requiring compileSdk 37+; unrelated to this plan's scope but required for any build/test verification to run).
- `:adflow-core` package `com.adflow.core`, must not depend on `play-services-ads` or any ad-network SDK.
- `:adflow-admob` package `com.adflow.admob`, depends on `:adflow-core` + `play-services-ads` (+ Compose UI, since Compose wrappers live here per spec §7).
- One `AdType` enum (`INTERSTITIAL, APP_OPEN, REWARDED, NATIVE, BANNER`) used everywhere — no second "full-screen only" enum.
- Retry defaults: `initialDelayMs=5_000, multiplier=2.0, maxDelayMs=60_000, maxRetries=5` (produces 5s/10s/20s/40s/60s).
- Show-interval defaults: `interstitialAfterInterstitialMs=5_000, appOpenAfterAppOpenMs=5_000, interstitialAfterAppOpenMs=3_000, appOpenAfterInterstitialMs=2_000`.
- Ad staleness default `expiryMs = 4h`, consulted only by full-screen managers (Interstitial/App Open/Rewarded).
- No app ever references AdMob types directly outside `:adflow-admob`; the swap point is `AdNetworkProvider`.
- No hard dependency on the Adjust SDK anywhere in `:adflow-core` or `:adflow-admob`.

---

### Task 1: Scaffold `:adflow-core` module

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `adflow-core/build.gradle.kts`
- Create: `adflow-core/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: an empty, buildable Android library module `:adflow-core` with namespace `com.adflow.core`, no ad SDK dependency, JUnit4 available for `src/test`.

- [ ] **Step 1: Add the `android-library` plugin alias to the version catalog**

Edit `gradle/libs.versions.toml`, in the `[plugins]` section, add a line right after `android-application`:

```toml
android-library = { id = "com.android.library", version.ref = "agp" }
```

- [ ] **Step 2: Register the module in settings**

Edit `settings.gradle.kts`:

```kotlin
rootProject.name = "AdFlow"
include(":app")
include(":adflow-core")
```

- [ ] **Step 3: Create the module build file**

Create `adflow-core/build.gradle.kts`:

```kotlin
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
}

dependencies {
    testImplementation(libs.junit)
}
```

- [ ] **Step 4: Create the module manifest**

Create `adflow-core/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 5: Verify the module builds**

Run: `./gradlew :adflow-core:build`
Expected: `BUILD SUCCESSFUL` (no source files yet, but the module configures and compiles).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml adflow-core
git commit -m "Scaffold :adflow-core library module"
```

---

### Task 2: Scaffold `:adflow-admob` module and wire `:app`

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `adflow-admob/build.gradle.kts`
- Create: `adflow-admob/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: `:adflow-core` from Task 1.
- Produces: an empty, buildable `:adflow-admob` module (namespace `com.adflow.admob`) depending on `:adflow-core`, Compose UI, and `play-services-ads`; `:app` depends on both new modules.

- [ ] **Step 1: Look up the current `play-services-ads` release**

Run: `curl -s https://dl.google.com/android/maven2/com/google/android/gms/play-services-ads/maven-metadata.xml | grep -o '<release>[^<]*</release>'`
Expected: a line like `<release>X.Y.Z</release>`. Use that exact version in the next step (do not guess).

- [ ] **Step 2: Add version catalog entries**

Edit `gradle/libs.versions.toml`. In `[versions]` add (replacing `X.Y.Z` with the value from Step 1):

```toml
playServicesAds = "X.Y.Z"
```

In `[libraries]` add:

```toml
play-services-ads = { group = "com.google.android.gms", name = "play-services-ads", version.ref = "playServicesAds" }
```

- [ ] **Step 3: Register the module in settings**

Edit `settings.gradle.kts`:

```kotlin
rootProject.name = "AdFlow"
include(":app")
include(":adflow-core")
include(":adflow-admob")
```

- [ ] **Step 4: Create the module build file**

Create `adflow-admob/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
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
}

dependencies {
    api(project(":adflow-core"))
    implementation(libs.play.services.ads)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
}
```

- [ ] **Step 5: Add the missing Compose Foundation catalog entry (needed for `AndroidView`)**

Check `gradle/libs.versions.toml` for an `androidx-compose-foundation` entry; if absent, add to `[libraries]`:

```toml
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
```

- [ ] **Step 6: Create the module manifest**

Create `adflow-admob/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 7: Wire `:app` to depend on both new modules**

Edit `app/build.gradle.kts`, in the `dependencies` block add (near the top, before the compose BOM lines):

```kotlin
    implementation(project(":adflow-core"))
    implementation(project(":adflow-admob"))
```

- [ ] **Step 8: Verify everything builds**

Run: `./gradlew :adflow-admob:build :app:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml adflow-admob app/build.gradle.kts
git commit -m "Scaffold :adflow-admob module and wire :app dependencies"
```

---

### Task 3: Core value types (`AdType`, errors, load result, callbacks)

**Files:**
- Create: `adflow-core/src/main/java/com/adflow/core/AdType.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/AdFlowError.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/AdLoadResult.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/BlockReason.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/RewardItem.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/ShowCallback.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/RewardedAdCallback.kt`
- Test: `adflow-core/src/test/java/com/adflow/core/ShowCallbackTest.kt`

**Interfaces:**
- Produces: `AdType`, `AdFlowError(code: Int, message: String)`, `AdLoadResult` (`Success` / `Failure(error)`), `BlockReason` (`DISABLED, RULE_REJECTED, INTERVAL_NOT_ELAPSED, NOT_READY`), `RewardItem(type: String, amount: Int)`, `ShowCallback` interface with `NONE` singleton, `RewardedAdCallback` interface — all consumed by every later task.

- [ ] **Step 1: Write the failing test for `ShowCallback.NONE`**

Create `adflow-core/src/test/java/com/adflow/core/ShowCallbackTest.kt`:

```kotlin
package com.adflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowCallbackTest {
    @Test
    fun `NONE default methods do not throw`() {
        var blockedReason: BlockReason? = null
        val callback = object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) {
                blockedReason = reason
            }
        }
        ShowCallback.NONE.onAdShown()
        ShowCallback.NONE.onAdDismissed()
        ShowCallback.NONE.onAdFailedToShow(AdFlowError(1, "x"))
        callback.onShowBlocked(BlockReason.NOT_READY)
        assertEquals(BlockReason.NOT_READY, blockedReason)
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails to compile**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.ShowCallbackTest"`
Expected: FAIL — `Unresolved reference` for `AdFlowError`, `BlockReason`, `ShowCallback`.

- [ ] **Step 3: Create `AdType.kt`**

```kotlin
package com.adflow.core

enum class AdType { INTERSTITIAL, APP_OPEN, REWARDED, NATIVE, BANNER }
```

- [ ] **Step 4: Create `AdFlowError.kt`**

```kotlin
package com.adflow.core

data class AdFlowError(val code: Int, val message: String)
```

- [ ] **Step 5: Create `AdLoadResult.kt`**

```kotlin
package com.adflow.core

sealed interface AdLoadResult {
    data object Success : AdLoadResult
    data class Failure(val error: AdFlowError) : AdLoadResult
}
```

- [ ] **Step 6: Create `BlockReason.kt`**

```kotlin
package com.adflow.core

enum class BlockReason { DISABLED, RULE_REJECTED, INTERVAL_NOT_ELAPSED, NOT_READY }
```

- [ ] **Step 7: Create `RewardItem.kt`**

```kotlin
package com.adflow.core

data class RewardItem(val type: String, val amount: Int)
```

- [ ] **Step 8: Create `ShowCallback.kt`**

```kotlin
package com.adflow.core

interface ShowCallback {
    fun onAdShown() {}
    fun onAdFailedToShow(error: AdFlowError) {}
    fun onAdDismissed() {}
    fun onShowBlocked(reason: BlockReason) {}

    companion object {
        val NONE: ShowCallback = object : ShowCallback {}
    }
}
```

- [ ] **Step 9: Create `RewardedAdCallback.kt`**

```kotlin
package com.adflow.core

interface RewardedAdCallback {
    fun onAdLoaded() {}
    fun onAdFailedToLoad(error: AdFlowError) {}
    fun onAdShown() {}
    fun onAdFailedToShow(error: AdFlowError) {}
    fun onUserEarnedReward(reward: RewardItem) {}
    fun onAdDismissed() {}
    fun onAdExpired() {}
    fun onShowBlocked(reason: BlockReason) {}

    companion object {
        val NONE: RewardedAdCallback = object : RewardedAdCallback {}
    }
}
```

- [ ] **Step 10: Run the test to confirm it passes**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.ShowCallbackTest"`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add adflow-core/src/main/java/com/adflow/core/AdType.kt \
        adflow-core/src/main/java/com/adflow/core/AdFlowError.kt \
        adflow-core/src/main/java/com/adflow/core/AdLoadResult.kt \
        adflow-core/src/main/java/com/adflow/core/BlockReason.kt \
        adflow-core/src/main/java/com/adflow/core/RewardItem.kt \
        adflow-core/src/main/java/com/adflow/core/ShowCallback.kt \
        adflow-core/src/main/java/com/adflow/core/RewardedAdCallback.kt \
        adflow-core/src/test/java/com/adflow/core/ShowCallbackTest.kt
git commit -m "Add core value types: AdType, AdFlowError, AdLoadResult, callbacks"
```

---

### Task 4: `PlacementConfig`, `AdRule`, `RetryPolicy`

**Files:**
- Create: `adflow-core/src/main/java/com/adflow/core/AdRule.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/RetryPolicy.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/PlacementConfig.kt`
- Test: `adflow-core/src/test/java/com/adflow/core/RetryPolicyTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AdRule` (`fun interface`), `RetryPolicy(initialDelayMs, multiplier, maxDelayMs, maxRetries)` with `delayForAttempt(attempt: Int): Long` and `RetryPolicy.DEFAULT`, `PlacementConfig(placementId, enabled, preloadEnabled, adUnitIds, retryPolicy, loadRule, showRule, expiryMs)` — consumed by `WaterfallLoader` (Task 5) and `FullScreenAdManagerBase` (Task 8).

- [ ] **Step 1: Write the failing test for backoff sequence**

Create `adflow-core/src/test/java/com/adflow/core/RetryPolicyTest.kt`:

```kotlin
package com.adflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun `default policy backs off 5s 10s 20s 40s 60s`() {
        val policy = RetryPolicy.DEFAULT
        assertEquals(5_000L, policy.delayForAttempt(1))
        assertEquals(10_000L, policy.delayForAttempt(2))
        assertEquals(20_000L, policy.delayForAttempt(3))
        assertEquals(40_000L, policy.delayForAttempt(4))
        assertEquals(60_000L, policy.delayForAttempt(5))
    }

    @Test
    fun `delay is capped at maxDelayMs beyond the cap point`() {
        val policy = RetryPolicy.DEFAULT
        assertEquals(60_000L, policy.delayForAttempt(6))
        assertEquals(60_000L, policy.delayForAttempt(10))
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.RetryPolicyTest"`
Expected: FAIL — `Unresolved reference: RetryPolicy`.

- [ ] **Step 3: Create `AdRule.kt`**

```kotlin
package com.adflow.core

fun interface AdRule {
    fun isAllowed(placementId: String): Boolean
}
```

- [ ] **Step 4: Create `RetryPolicy.kt`**

```kotlin
package com.adflow.core

import kotlin.math.pow

data class RetryPolicy(
    val initialDelayMs: Long = 5_000,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = 60_000,
    val maxRetries: Int = 5,
) {
    fun delayForAttempt(attempt: Int): Long {
        val raw = initialDelayMs * multiplier.pow((attempt - 1).coerceAtLeast(0))
        return raw.toLong().coerceAtMost(maxDelayMs)
    }

    companion object {
        val DEFAULT = RetryPolicy()
    }
}
```

- [ ] **Step 5: Create `PlacementConfig.kt`**

```kotlin
package com.adflow.core

data class PlacementConfig(
    val placementId: String,
    val enabled: Boolean = true,
    val preloadEnabled: Boolean = true,
    val adUnitIds: List<String>,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    val loadRule: AdRule? = null,
    val showRule: AdRule? = null,
    val expiryMs: Long = 4 * 60 * 60 * 1000L,
) {
    init {
        require(adUnitIds.isNotEmpty()) { "adUnitIds must not be empty for placement '$placementId'" }
    }
}
```

- [ ] **Step 6: Run the test to confirm it passes**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.RetryPolicyTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add adflow-core/src/main/java/com/adflow/core/AdRule.kt \
        adflow-core/src/main/java/com/adflow/core/RetryPolicy.kt \
        adflow-core/src/main/java/com/adflow/core/PlacementConfig.kt \
        adflow-core/src/test/java/com/adflow/core/RetryPolicyTest.kt
git commit -m "Add PlacementConfig, AdRule, and RetryPolicy backoff calculation"
```

---

### Task 5: `WaterfallLoader`

**Files:**
- Create: `adflow-core/src/main/java/com/adflow/core/WaterfallLoader.kt`
- Test: `adflow-core/src/test/java/com/adflow/core/WaterfallLoaderTest.kt`

**Interfaces:**
- Consumes: nothing new (generic over `TAd`).
- Produces: `WaterfallLoader<TAd>(adUnitIds: List<String>, attemptLoad: (String, (Result<TAd>) -> Unit) -> Unit)` with `start(onFinalResult: (Result<TAd>) -> Unit)` — consumed by `FullScreenAdManagerBase` (Task 8) and the Banner manager (Task 16).

- [ ] **Step 1: Write the failing tests**

Create `adflow-core/src/test/java/com/adflow/core/WaterfallLoaderTest.kt`:

```kotlin
package com.adflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterfallLoaderTest {
    @Test
    fun `succeeds on first ad unit without trying others`() {
        val attempted = mutableListOf<String>()
        val loader = WaterfallLoader<String>(listOf("A", "B", "C")) { id, onResult ->
            attempted += id
            onResult(Result.success("ad-$id"))
        }
        var finalResult: Result<String>? = null
        loader.start { finalResult = it }
        assertEquals(listOf("A"), attempted)
        assertEquals("ad-A", finalResult?.getOrNull())
    }

    @Test
    fun `falls through to next ad unit on failure`() {
        val attempted = mutableListOf<String>()
        val loader = WaterfallLoader<String>(listOf("A", "B", "C")) { id, onResult ->
            attempted += id
            if (id == "B") onResult(Result.success("ad-B")) else onResult(Result.failure(RuntimeException("no fill")))
        }
        var finalResult: Result<String>? = null
        loader.start { finalResult = it }
        assertEquals(listOf("A", "B"), attempted)
        assertEquals("ad-B", finalResult?.getOrNull())
    }

    @Test
    fun `fails when every ad unit in the list fails`() {
        val attempted = mutableListOf<String>()
        val loader = WaterfallLoader<String>(listOf("A", "B")) { id, onResult ->
            attempted += id
            onResult(Result.failure(RuntimeException("no fill $id")))
        }
        var finalResult: Result<String>? = null
        loader.start { finalResult = it }
        assertEquals(listOf("A", "B"), attempted)
        assertTrue(finalResult?.isFailure == true)
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.WaterfallLoaderTest"`
Expected: FAIL — `Unresolved reference: WaterfallLoader`.

- [ ] **Step 3: Implement `WaterfallLoader.kt`**

```kotlin
package com.adflow.core

class WaterfallLoader<TAd>(
    private val adUnitIds: List<String>,
    private val attemptLoad: (adUnitId: String, onResult: (Result<TAd>) -> Unit) -> Unit,
) {
    fun start(onFinalResult: (Result<TAd>) -> Unit) {
        tryIndex(0, onFinalResult)
    }

    private fun tryIndex(index: Int, onFinalResult: (Result<TAd>) -> Unit) {
        if (index >= adUnitIds.size) {
            onFinalResult(Result.failure(NoSuchElementException("Waterfall exhausted: no ad units left")))
            return
        }
        attemptLoad(adUnitIds[index]) { result ->
            if (result.isSuccess) {
                onFinalResult(result)
            } else {
                tryIndex(index + 1, onFinalResult)
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.WaterfallLoaderTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add adflow-core/src/main/java/com/adflow/core/WaterfallLoader.kt \
        adflow-core/src/test/java/com/adflow/core/WaterfallLoaderTest.kt
git commit -m "Add WaterfallLoader with ordered fallback across ad unit IDs"
```

---

### Task 6: `ShowIntervalConfig` + `AdShowIntervalPolicy`

**Files:**
- Create: `adflow-core/src/main/java/com/adflow/core/ShowIntervalConfig.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/AdShowIntervalPolicy.kt`
- Test: `adflow-core/src/test/java/com/adflow/core/AdShowIntervalPolicyTest.kt`

**Interfaces:**
- Consumes: `AdType` (Task 3).
- Produces: `ShowIntervalConfig(...)` with the four gap defaults, `internal object AdShowIntervalPolicy` with `configure(config)`, `canShow(type, now)`, `recordShown(type, now)`, and an `internal fun reset()` for test isolation — consumed by `FullScreenAdManagerBase` (Task 8).

- [ ] **Step 1: Write the failing tests**

Create `adflow-core/src/test/java/com/adflow/core/AdShowIntervalPolicyTest.kt`:

```kotlin
package com.adflow.core

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdShowIntervalPolicyTest {

    @After
    fun tearDown() {
        AdShowIntervalPolicy.reset()
    }

    @Test
    fun `same type must wait the configured gap`() {
        AdShowIntervalPolicy.configure(ShowIntervalConfig(interstitialAfterInterstitialMs = 5_000))
        AdShowIntervalPolicy.recordShown(AdType.INTERSTITIAL, now = 0L)
        assertFalse(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 4_999L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 5_000L))
    }

    @Test
    fun `interstitial after app open uses the cross-type gap`() {
        AdShowIntervalPolicy.configure(ShowIntervalConfig(interstitialAfterAppOpenMs = 3_000))
        AdShowIntervalPolicy.recordShown(AdType.APP_OPEN, now = 0L)
        assertFalse(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 2_999L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 3_000L))
    }

    @Test
    fun `app open after interstitial uses the cross-type gap`() {
        AdShowIntervalPolicy.configure(ShowIntervalConfig(appOpenAfterInterstitialMs = 2_000))
        AdShowIntervalPolicy.recordShown(AdType.INTERSTITIAL, now = 0L)
        assertFalse(AdShowIntervalPolicy.canShow(AdType.APP_OPEN, now = 1_999L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.APP_OPEN, now = 2_000L))
    }

    @Test
    fun `types other than interstitial and app open are never capped`() {
        AdShowIntervalPolicy.recordShown(AdType.INTERSTITIAL, now = 0L)
        assertTrue(AdShowIntervalPolicy.canShow(AdType.REWARDED, now = 1L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.NATIVE, now = 1L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.BANNER, now = 1L))
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.AdShowIntervalPolicyTest"`
Expected: FAIL — `Unresolved reference: ShowIntervalConfig` / `AdShowIntervalPolicy`.

- [ ] **Step 3: Create `ShowIntervalConfig.kt`**

```kotlin
package com.adflow.core

data class ShowIntervalConfig(
    val interstitialAfterInterstitialMs: Long = 5_000,
    val appOpenAfterAppOpenMs: Long = 5_000,
    val interstitialAfterAppOpenMs: Long = 3_000,
    val appOpenAfterInterstitialMs: Long = 2_000,
)
```

- [ ] **Step 4: Implement `AdShowIntervalPolicy.kt`**

```kotlin
package com.adflow.core

internal object AdShowIntervalPolicy {
    private var config = ShowIntervalConfig()
    private var lastInterstitialShownAt: Long? = null
    private var lastAppOpenShownAt: Long? = null

    fun configure(config: ShowIntervalConfig) {
        this.config = config
    }

    fun canShow(type: AdType, now: Long = System.currentTimeMillis()): Boolean = when (type) {
        AdType.INTERSTITIAL -> {
            val sameTypeOk = lastInterstitialShownAt?.let { now - it >= config.interstitialAfterInterstitialMs } ?: true
            val crossTypeOk = lastAppOpenShownAt?.let { now - it >= config.interstitialAfterAppOpenMs } ?: true
            sameTypeOk && crossTypeOk
        }
        AdType.APP_OPEN -> {
            val sameTypeOk = lastAppOpenShownAt?.let { now - it >= config.appOpenAfterAppOpenMs } ?: true
            val crossTypeOk = lastInterstitialShownAt?.let { now - it >= config.appOpenAfterInterstitialMs } ?: true
            sameTypeOk && crossTypeOk
        }
        else -> true
    }

    fun recordShown(type: AdType, now: Long = System.currentTimeMillis()) {
        when (type) {
            AdType.INTERSTITIAL -> lastInterstitialShownAt = now
            AdType.APP_OPEN -> lastAppOpenShownAt = now
            else -> Unit
        }
    }

    internal fun reset() {
        config = ShowIntervalConfig()
        lastInterstitialShownAt = null
        lastAppOpenShownAt = null
    }
}
```

- [ ] **Step 5: Run the tests to confirm they pass**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.AdShowIntervalPolicyTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add adflow-core/src/main/java/com/adflow/core/ShowIntervalConfig.kt \
        adflow-core/src/main/java/com/adflow/core/AdShowIntervalPolicy.kt \
        adflow-core/src/test/java/com/adflow/core/AdShowIntervalPolicyTest.kt
git commit -m "Add AdShowIntervalPolicy for Interstitial/App Open frequency capping"
```

---

### Task 7: Logging, revenue tracking, and `AdFlowCore`

**Files:**
- Create: `adflow-core/src/main/java/com/adflow/core/AdFlowEvent.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/AdFlowLogger.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/LogcatAdFlowLogger.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/AdRevenueEvent.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/RevenueLogger.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/AdFlowCore.kt`
- Test: `adflow-core/src/test/java/com/adflow/core/AdFlowCoreTest.kt`

**Interfaces:**
- Consumes: `AdType` (Task 3), `ShowIntervalConfig`/`AdShowIntervalPolicy` (Task 6).
- Produces: `AdFlowEvent` enum, `AdFlowLogger` interface + `LogcatAdFlowLogger`, `AdRevenueEvent`, `RevenueLogger`, and the `AdFlowCore` object (`configure(...)`, `logger`, `addRevenueLogger`, `removeRevenueLogger`, `dispatchRevenue`, `internal fun reset()`) — consumed by every manager task (8, 13-17).

`LogcatAdFlowLogger` needs `android.util.Log`, which requires Robolectric to unit test; keep it trivial (one line) and cover only the non-Android logic (`AdFlowCore`) with a JVM test.

- [ ] **Step 1: Write the failing test for revenue dispatch and reset**

Create `adflow-core/src/test/java/com/adflow/core/AdFlowCoreTest.kt`:

```kotlin
package com.adflow.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AdFlowCoreTest {

    @After
    fun tearDown() {
        AdFlowCore.reset()
    }

    @Test
    fun `dispatches revenue events to every registered logger`() {
        val received = mutableListOf<AdRevenueEvent>()
        val loggerA = RevenueLogger { received += it }
        val loggerB = RevenueLogger { received += it }
        AdFlowCore.addRevenueLogger(loggerA)
        AdFlowCore.addRevenueLogger(loggerB)

        val event = AdRevenueEvent(
            placementId = "splash_interstitial",
            adType = AdType.INTERSTITIAL,
            adUnitId = "unit-1",
            valueMicros = 1_000_000,
            currencyCode = "USD",
            precision = "ESTIMATED",
            adNetwork = "AdMob",
        )
        AdFlowCore.dispatchRevenue(event)

        assertEquals(listOf(event, event), received)
    }

    @Test
    fun `removed logger no longer receives events`() {
        val received = mutableListOf<AdRevenueEvent>()
        val logger = RevenueLogger { received += it }
        AdFlowCore.addRevenueLogger(logger)
        AdFlowCore.removeRevenueLogger(logger)

        AdFlowCore.dispatchRevenue(
            AdRevenueEvent("p", AdType.BANNER, "u", 1, "USD", "ESTIMATED", null)
        )

        assertEquals(0, received.size)
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.AdFlowCoreTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Create `AdFlowEvent.kt`**

```kotlin
package com.adflow.core

enum class AdFlowEvent {
    LOADING, LOADED, LOAD_FAILED, RETRYING, WATERFALL_NEXT,
    SHOWN, SHOW_BLOCKED, SHOW_FAILED, NO_FILL, EXPIRED,
}
```

- [ ] **Step 4: Create `AdFlowLogger.kt`**

```kotlin
package com.adflow.core

interface AdFlowLogger {
    fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String? = null)
}
```

- [ ] **Step 5: Create `LogcatAdFlowLogger.kt`**

```kotlin
package com.adflow.core

import android.util.Log

class LogcatAdFlowLogger(private val tag: String = "AdFlow") : AdFlowLogger {
    override fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String?) {
        Log.d(tag, "[$adType/$placementId] $event${detail?.let { " ($it)" } ?: ""}")
    }
}
```

- [ ] **Step 6: Create `AdRevenueEvent.kt`**

```kotlin
package com.adflow.core

data class AdRevenueEvent(
    val placementId: String,
    val adType: AdType,
    val adUnitId: String,
    val valueMicros: Long,
    val currencyCode: String,
    val precision: String,
    val adNetwork: String?,
)
```

- [ ] **Step 7: Create `RevenueLogger.kt`**

```kotlin
package com.adflow.core

fun interface RevenueLogger {
    fun onRevenuePaid(event: AdRevenueEvent)
}
```

- [ ] **Step 8: Implement `AdFlowCore.kt`**

```kotlin
package com.adflow.core

object AdFlowCore {
    var logger: AdFlowLogger = LogcatAdFlowLogger()
        private set

    private val revenueLoggers = mutableListOf<RevenueLogger>()

    fun configure(
        showIntervalConfig: ShowIntervalConfig = ShowIntervalConfig(),
        logger: AdFlowLogger = LogcatAdFlowLogger(),
    ) {
        this.logger = logger
        AdShowIntervalPolicy.configure(showIntervalConfig)
    }

    fun addRevenueLogger(logger: RevenueLogger) {
        revenueLoggers += logger
    }

    fun removeRevenueLogger(logger: RevenueLogger) {
        revenueLoggers -= logger
    }

    fun dispatchRevenue(event: AdRevenueEvent) {
        revenueLoggers.forEach { it.onRevenuePaid(event) }
    }

    internal fun reset() {
        logger = LogcatAdFlowLogger()
        revenueLoggers.clear()
        AdShowIntervalPolicy.reset()
    }
}
```

- [ ] **Step 9: Run the test to confirm it passes**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.AdFlowCoreTest"`
Expected: PASS (2 tests).

- [ ] **Step 10: Commit**

```bash
git add adflow-core/src/main/java/com/adflow/core/AdFlowEvent.kt \
        adflow-core/src/main/java/com/adflow/core/AdFlowLogger.kt \
        adflow-core/src/main/java/com/adflow/core/LogcatAdFlowLogger.kt \
        adflow-core/src/main/java/com/adflow/core/AdRevenueEvent.kt \
        adflow-core/src/main/java/com/adflow/core/RevenueLogger.kt \
        adflow-core/src/main/java/com/adflow/core/AdFlowCore.kt \
        adflow-core/src/test/java/com/adflow/core/AdFlowCoreTest.kt
git commit -m "Add AdFlowLogger, revenue tracking, and the AdFlowCore global config object"
```

---

### Task 8: `FullScreenAdManager` interface + `FullScreenAdManagerBase`

This is the central orchestration class: enabled/rule checks, waterfall load, incremental retry, expiry, show-interval gating, and auto-preload after show. It must be fully unit-testable, so the retry scheduler and clock are injectable.

**Files:**
- Create: `adflow-core/src/main/java/com/adflow/core/FullScreenAdManager.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/FullScreenAdManagerBase.kt`
- Test: `adflow-core/src/test/java/com/adflow/core/FullScreenAdManagerBaseTest.kt`

**Interfaces:**
- Consumes: `PlacementConfig`, `AdRule`, `RetryPolicy` (Task 4), `WaterfallLoader` (Task 5), `AdShowIntervalPolicy` (Task 6), `AdFlowCore`, `AdFlowEvent` (Task 7), `AdType`, `AdFlowError`, `AdLoadResult`, `BlockReason`, `ShowCallback` (Task 3).
- Produces: `FullScreenAdManager` interface (`isReady`, `load`, `show`) and `abstract class FullScreenAdManagerBase<TAd : Any>(config, adType)` with `protected abstract fun requestAd(adUnitId, onResult)`, `protected abstract fun performShow(ad, activity, callback)`, and test-only injectable `internal var scheduleRetry`, `internal var nowProvider` — consumed by Task 9 (typed manager interfaces) and Tasks 13-15 (AdMob full-screen managers).

Note: `show()` takes a real `android.app.Activity`, which cannot be instantiated against the plain Android stub jar used by default JVM unit tests. Add Robolectric to `:adflow-core`'s test dependencies so tests can obtain a real `Activity` instance via `Robolectric.buildActivity(Activity::class.java).get()`.

- [ ] **Step 1: Add Robolectric to `:adflow-core` test dependencies**

Add to `gradle/libs.versions.toml` `[versions]`:

```toml
robolectric = "4.14.1"
```

Add to `[libraries]`:

```toml
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
```

Edit `adflow-core/build.gradle.kts`, add to `dependencies`:

```kotlin
    testImplementation(libs.robolectric)
```

And add `testOptions` inside the `android {}` block:

```kotlin
    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
```

- [ ] **Step 2: Write the failing tests**

Create `adflow-core/src/test/java/com/adflow/core/FullScreenAdManagerBaseTest.kt`:

```kotlin
package com.adflow.core

import android.app.Activity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FullScreenAdManagerBaseTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    private class FakeManager(
        config: PlacementConfig,
        private val loadResults: MutableMap<String, Result<String>>,
    ) : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
        var shownAd: String? = null

        override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
            onResult(loadResults[adUnitId] ?: Result.failure(RuntimeException("no fill")))
        }

        override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
            shownAd = ad
            callback.onAdShown()
            callback.onAdDismissed()
        }
    }

    @After
    fun tearDown() {
        AdFlowCore.reset()
    }

    @Test
    fun `loads successfully on the first ad unit`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A", "B"))
        val manager = FakeManager(config, mutableMapOf("A" to Result.success("ad-A")))
        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(AdLoadResult.Success, result)
        assertTrue(manager.isReady())
    }

    @Test
    fun `falls through the waterfall then reports failure once retries are exhausted`() {
        val fakeScheduler = mutableListOf<() -> Unit>()
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A", "B"),
            retryPolicy = RetryPolicy(initialDelayMs = 1, multiplier = 1.0, maxDelayMs = 1, maxRetries = 1),
        )
        val manager = FakeManager(config, mutableMapOf())
        manager.scheduleRetry = { _, action -> fakeScheduler += action }

        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(null, result) // still retrying, waiting on the scheduler
        assertEquals(1, fakeScheduler.size)

        fakeScheduler.removeAt(0).invoke() // run the retry synchronously
        assertTrue(result is AdLoadResult.Failure)
        assertFalse(manager.isReady())
    }

    @Test
    fun `show is blocked when the placement is not ready`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = FakeManager(config, mutableMapOf())
        var blockedReason: BlockReason? = null
        manager.show(activity, object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })
        assertEquals(BlockReason.NOT_READY, blockedReason)
    }

    @Test
    fun `show is blocked by a rejecting showRule even when ready`() {
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A"),
            showRule = AdRule { false },
        )
        val manager = FakeManager(config, mutableMapOf("A" to Result.success("ad-A")))
        manager.load {}
        var blockedReason: BlockReason? = null
        manager.show(activity, object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })
        assertEquals(BlockReason.RULE_REJECTED, blockedReason)
        assertEquals(null, manager.shownAd)
    }

    @Test
    fun `show is blocked when the show-interval has not elapsed`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = FakeManager(config, mutableMapOf("A" to Result.success("ad-A")))
        manager.nowProvider = { 0L }
        manager.load {}
        AdShowIntervalPolicy.recordShown(AdType.INTERSTITIAL, now = 0L)

        var blockedReason: BlockReason? = null
        manager.show(activity, object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })
        assertEquals(BlockReason.INTERVAL_NOT_ELAPSED, blockedReason)
    }

    @Test
    fun `successful show consumes the cached ad and preloads again when enabled`() {
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A"),
            preloadEnabled = true,
        )
        var loadCount = 0
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                loadCount += 1
                onResult(Result.success("ad-$loadCount"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                callback.onAdShown()
            }
        }
        manager.load {}
        assertEquals(1, loadCount)
        manager.show(activity, ShowCallback.NONE)
        assertEquals(2, loadCount) // preload triggered a second load
    }
}
```

`AdShowIntervalPolicy` is `internal` in `:adflow-core`; the unit test source set compiles against the module's internal declarations, so it can call `AdShowIntervalPolicy.recordShown(...)` directly, as already written above.

- [ ] **Step 3: Run the tests to confirm they fail**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.FullScreenAdManagerBaseTest"`
Expected: FAIL — `Unresolved reference: FullScreenAdManagerBase`.

- [ ] **Step 4: Create `FullScreenAdManager.kt`**

```kotlin
package com.adflow.core

import android.app.Activity

interface FullScreenAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun show(activity: Activity, callback: ShowCallback = ShowCallback.NONE)
}
```

- [ ] **Step 5: Implement `FullScreenAdManagerBase.kt`**

```kotlin
package com.adflow.core

import android.app.Activity

abstract class FullScreenAdManagerBase<TAd : Any>(
    private val config: PlacementConfig,
    private val adType: AdType,
) : FullScreenAdManager {

    protected abstract fun requestAd(adUnitId: String, onResult: (Result<TAd>) -> Unit)
    protected abstract fun performShow(ad: TAd, activity: Activity, callback: ShowCallback)

    internal var scheduleRetry: (delayMs: Long, action: () -> Unit) -> Unit =
        { delayMs, action -> android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, delayMs) }
    internal var nowProvider: () -> Long = { System.currentTimeMillis() }

    private var cachedAd: TAd? = null
    private var loadedAtMs: Long = 0L
    private var retryAttempt: Int = 0
    private var isLoading: Boolean = false

    override fun isReady(): Boolean {
        val ageMs = nowProvider() - loadedAtMs
        return cachedAd != null && ageMs < config.expiryMs
    }

    override fun load(onResult: (AdLoadResult) -> Unit) {
        if (!config.enabled) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOAD_FAILED, "disabled")
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
            return
        }
        if (config.loadRule?.isAllowed(config.placementId) == false) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOAD_FAILED, "loadRule rejected")
            onResult(AdLoadResult.Failure(AdFlowError(-2, "load rule rejected")))
            return
        }
        if (isLoading) return
        isLoading = true
        retryAttempt = 0
        startWaterfall(onResult)
    }

    private fun startWaterfall(onResult: (AdLoadResult) -> Unit) {
        AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOADING)
        val loader = WaterfallLoader(config.adUnitIds) { adUnitId, cb ->
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.WATERFALL_NEXT, adUnitId)
            requestAd(adUnitId, cb)
        }
        loader.start { result ->
            result.fold(
                onSuccess = { ad ->
                    cachedAd = ad
                    loadedAtMs = nowProvider()
                    isLoading = false
                    retryAttempt = 0
                    AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOADED)
                    onResult(AdLoadResult.Success)
                },
                onFailure = { error ->
                    AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.NO_FILL)
                    retryAttempt += 1
                    if (retryAttempt > config.retryPolicy.maxRetries) {
                        isLoading = false
                        onResult(AdLoadResult.Failure(AdFlowError(-3, error.message ?: "waterfall exhausted")))
                        return@fold
                    }
                    val delayMs = config.retryPolicy.delayForAttempt(retryAttempt)
                    AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.RETRYING, "attempt=$retryAttempt delay=$delayMs")
                    scheduleRetry(delayMs) { startWaterfall(onResult) }
                },
            )
        }
    }

    override fun show(activity: Activity, callback: ShowCallback) {
        if (!isReady()) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "not ready")
            callback.onShowBlocked(BlockReason.NOT_READY)
            return
        }
        if (config.showRule?.isAllowed(config.placementId) == false) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "showRule rejected")
            callback.onShowBlocked(BlockReason.RULE_REJECTED)
            return
        }
        if (!AdShowIntervalPolicy.canShow(adType, nowProvider())) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "interval not elapsed")
            callback.onShowBlocked(BlockReason.INTERVAL_NOT_ELAPSED)
            return
        }
        val ad = cachedAd ?: return
        cachedAd = null
        AdShowIntervalPolicy.recordShown(adType, nowProvider())
        AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOWN)
        performShow(ad, activity, callback)
        if (config.preloadEnabled) load()
    }
}
```

- [ ] **Step 6: Run the tests to confirm they pass**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.FullScreenAdManagerBaseTest"`
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml adflow-core/build.gradle.kts \
        adflow-core/src/main/java/com/adflow/core/FullScreenAdManager.kt \
        adflow-core/src/main/java/com/adflow/core/FullScreenAdManagerBase.kt \
        adflow-core/src/test/java/com/adflow/core/FullScreenAdManagerBaseTest.kt
git commit -m "Add FullScreenAdManagerBase orchestrating waterfall/retry/expiry/show-interval"
```

---

### Task 9: Typed full-screen manager interfaces

**Files:**
- Create: `adflow-core/src/main/java/com/adflow/core/InterstitialAdManager.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/AppOpenAdManager.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/RewardedAdManager.kt`
- Test: `adflow-core/src/test/java/com/adflow/core/RewardedAdManagerContractTest.kt`

**Interfaces:**
- Consumes: `FullScreenAdManager` (Task 8), `RewardedAdCallback` (Task 3).
- Produces: `InterstitialAdManager`, `AppOpenAdManager` (both just `FullScreenAdManager` marker interfaces — same `show(activity, ShowCallback)` signature), and `RewardedAdManager` which overloads `show` to accept a `RewardedAdCallback` — consumed by `AdNetworkProvider` (Task 11) and the AdMob implementations (Tasks 13-15).

- [ ] **Step 1: Write the failing test asserting the Rewarded contract shape**

Create `adflow-core/src/test/java/com/adflow/core/RewardedAdManagerContractTest.kt`:

```kotlin
package com.adflow.core

import android.app.Activity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RewardedAdManagerContractTest {

    private class FakeRewarded : RewardedAdManager {
        override fun isReady(): Boolean = true
        override fun load(onResult: (AdLoadResult) -> Unit) = onResult(AdLoadResult.Success)
        override fun show(activity: Activity, callback: RewardedAdCallback) {
            callback.onUserEarnedReward(RewardItem("coins", 10))
        }
    }

    @Test
    fun `rewarded manager show delivers a reward through the rewarded callback`() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        var earned: RewardItem? = null
        FakeRewarded().show(activity, object : RewardedAdCallback {
            override fun onUserEarnedReward(reward: RewardItem) { earned = reward }
        })
        assertTrue(earned == RewardItem("coins", 10))
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.RewardedAdManagerContractTest"`
Expected: FAIL — `Unresolved reference: RewardedAdManager`.

- [ ] **Step 3: Create `InterstitialAdManager.kt`**

```kotlin
package com.adflow.core

interface InterstitialAdManager : FullScreenAdManager
```

- [ ] **Step 4: Create `AppOpenAdManager.kt`**

```kotlin
package com.adflow.core

interface AppOpenAdManager : FullScreenAdManager
```

- [ ] **Step 5: Create `RewardedAdManager.kt`**

```kotlin
package com.adflow.core

import android.app.Activity

interface RewardedAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun show(activity: Activity, callback: RewardedAdCallback = RewardedAdCallback.NONE)
}
```

- [ ] **Step 6: Run the test to confirm it passes**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.RewardedAdManagerContractTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add adflow-core/src/main/java/com/adflow/core/InterstitialAdManager.kt \
        adflow-core/src/main/java/com/adflow/core/AppOpenAdManager.kt \
        adflow-core/src/main/java/com/adflow/core/RewardedAdManager.kt \
        adflow-core/src/test/java/com/adflow/core/RewardedAdManagerContractTest.kt
git commit -m "Add typed Interstitial/AppOpen/Rewarded manager interfaces"
```

---

### Task 10: Native & Banner contracts

**Files:**
- Create: `adflow-core/src/main/java/com/adflow/core/NativeAdAssets.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/NativeAdRenderer.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/NativeAdManager.kt`
- Create: `adflow-core/src/main/java/com/adflow/core/BannerAdManager.kt`
- Test: `adflow-core/src/test/java/com/adflow/core/NativeAdRendererContractTest.kt`

**Interfaces:**
- Consumes: `AdLoadResult` (Task 3).
- Produces: `NativeAdAssets(headline, body, iconUri, callToAction, starRating, advertiser, mediaViewSlot)`, `NativeAdRenderer` (`createView`, `bind`), `NativeAdManager` (`isReady`, `load`, `createView(context, renderer)`), `BannerAdManager` (`isReady`, `load`, `getView(context)`) — consumed by `AdNetworkProvider` (Task 11) and the AdMob Native/Banner implementations (Tasks 16-19).

- [ ] **Step 1: Write the failing test for the renderer contract**

Create `adflow-core/src/test/java/com/adflow/core/NativeAdRendererContractTest.kt`:

```kotlin
package com.adflow.core

import android.content.Context
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeAdRendererContractTest {

    private class SimpleTextRenderer : NativeAdRenderer {
        override fun createView(context: Context): View = TextView(context)
        override fun bind(view: View, assets: NativeAdAssets) {
            (view as TextView).text = assets.headline
        }
    }

    @Test
    fun `renderer binds headline text onto the created view`() {
        val context = RuntimeEnvironment.getApplication()
        val renderer = SimpleTextRenderer()
        val view = renderer.createView(context)
        val assets = NativeAdAssets(
            headline = "Great app",
            body = null,
            iconUri = null,
            callToAction = null,
            starRating = null,
            advertiser = null,
            mediaViewSlot = { ctx -> View(ctx) },
        )
        renderer.bind(view, assets)
        assertEquals("Great app", (view as TextView).text)
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.NativeAdRendererContractTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Create `NativeAdAssets.kt`**

```kotlin
package com.adflow.core

import android.content.Context
import android.view.View

data class NativeAdAssets(
    val headline: String,
    val body: String?,
    val iconUri: String?,
    val callToAction: String?,
    val starRating: Double?,
    val advertiser: String?,
    val mediaViewSlot: (Context) -> View,
)
```

- [ ] **Step 4: Create `NativeAdRenderer.kt`**

```kotlin
package com.adflow.core

import android.content.Context
import android.view.View

interface NativeAdRenderer {
    fun createView(context: Context): View
    fun bind(view: View, assets: NativeAdAssets)
}
```

- [ ] **Step 5: Create `NativeAdManager.kt`**

```kotlin
package com.adflow.core

import android.content.Context
import android.view.View

interface NativeAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun createView(context: Context, renderer: NativeAdRenderer): View
}
```

- [ ] **Step 6: Create `BannerAdManager.kt`**

```kotlin
package com.adflow.core

import android.content.Context
import android.view.View

interface BannerAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun getView(context: Context): View
}
```

- [ ] **Step 7: Run the test to confirm it passes**

Run: `./gradlew :adflow-core:testDebugUnitTest --tests "com.adflow.core.NativeAdRendererContractTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add adflow-core/src/main/java/com/adflow/core/NativeAdAssets.kt \
        adflow-core/src/main/java/com/adflow/core/NativeAdRenderer.kt \
        adflow-core/src/main/java/com/adflow/core/NativeAdManager.kt \
        adflow-core/src/main/java/com/adflow/core/BannerAdManager.kt \
        adflow-core/src/test/java/com/adflow/core/NativeAdRendererContractTest.kt
git commit -m "Add Native/Banner manager and renderer contracts"
```

---

### Task 11: `AdNetworkProvider`

**Files:**
- Create: `adflow-core/src/main/java/com/adflow/core/AdNetworkProvider.kt`

**Interfaces:**
- Consumes: `PlacementConfig` (Task 4), `InterstitialAdManager`/`AppOpenAdManager`/`RewardedAdManager` (Task 9), `NativeAdManager`/`BannerAdManager` (Task 10).
- Produces: `AdNetworkProvider` interface — implemented by `AdMobProvider` in Task 18; this is the single swap point the host app depends on.

No new behavior to unit test here (a pure interface); verify via compilation only.

- [ ] **Step 1: Create `AdNetworkProvider.kt`**

```kotlin
package com.adflow.core

import android.content.Context

interface AdNetworkProvider {
    fun initialize(context: Context, onComplete: () -> Unit = {})
    fun createInterstitial(config: PlacementConfig): InterstitialAdManager
    fun createAppOpen(config: PlacementConfig): AppOpenAdManager
    fun createRewarded(config: PlacementConfig): RewardedAdManager
    fun createNative(config: PlacementConfig): NativeAdManager
    fun createBanner(config: PlacementConfig): BannerAdManager
}
```

- [ ] **Step 2: Verify the module still builds**

Run: `./gradlew :adflow-core:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add adflow-core/src/main/java/com/adflow/core/AdNetworkProvider.kt
git commit -m "Add AdNetworkProvider as the single ad-network swap point"
```

---

### Task 12: `:adflow-core` full module verification

A checkpoint task: confirm every unit test across the whole `:adflow-core` module passes together before moving to the AdMob implementation.

**Files:** none (verification only).

- [ ] **Step 1: Run the full `:adflow-core` test suite**

Run: `./gradlew :adflow-core:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests from Tasks 3-10 passing (17 tests total: 1 + 2 + 3 + 4 + 2 + 6 + 1 + 1).

- [ ] **Step 2: If anything fails, fix forward**

Do not proceed to Task 13 until this is green. Fix the specific failing file (there are no other files left to touch in `:adflow-core` at this point), re-run, and only commit the fix once green.

---

### Task 13: `AdMobInterstitialAdManager`

**Files:**
- Create: `adflow-admob/src/main/java/com/adflow/admob/fullscreen/AdMobInterstitialAdManager.kt`

**Interfaces:**
- Consumes: `FullScreenAdManagerBase`, `PlacementConfig`, `AdType`, `AdFlowError`, `ShowCallback`, `AdFlowCore`, `AdRevenueEvent` (all `:adflow-core`); `com.google.android.gms.ads.*`, `com.google.android.gms.ads.interstitial.*` (`play-services-ads`).
- Produces: `AdMobInterstitialAdManager(context: Context, config: PlacementConfig) : InterstitialAdManager` — consumed by `AdMobProvider` (Task 18).

This class calls real AdMob APIs and cannot be unit tested without a live ad request; verify by compilation, and by manual smoke test once the demo app exists (Task 21).

- [ ] **Step 1: Implement `AdMobInterstitialAdManager.kt`**

```kotlin
package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.FullScreenAdManagerBase
import com.adflow.core.InterstitialAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.ShowCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class AdMobInterstitialAdManager(
    private val context: Context,
    config: PlacementConfig,
) : FullScreenAdManagerBase<InterstitialAd>(config, AdType.INTERSTITIAL), InterstitialAdManager {

    private val placementId = config.placementId

    override fun requestAd(adUnitId: String, onResult: (Result<InterstitialAd>) -> Unit) {
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.onPaidEventListener = com.google.android.gms.ads.OnPaidEventListener { adValue ->
                        AdFlowCore.dispatchRevenue(
                            AdRevenueEvent(
                                placementId = placementId,
                                adType = AdType.INTERSTITIAL,
                                adUnitId = adUnitId,
                                valueMicros = adValue.valueMicros,
                                currencyCode = adValue.currencyCode,
                                precision = adValue.precisionType.toString(),
                                adNetwork = ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                            ),
                        )
                    }
                    onResult(Result.success(ad))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    onResult(Result.failure(RuntimeException(error.message)))
                }
            },
        )
    }

    override fun performShow(ad: InterstitialAd, activity: Activity, callback: ShowCallback) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()
            override fun onAdDismissedFullScreenContent() = callback.onAdDismissed()
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                callback.onAdFailedToShow(AdFlowError(error.code, error.message))
        }
        ad.show(activity)
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :adflow-admob:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add adflow-admob/src/main/java/com/adflow/admob/fullscreen/AdMobInterstitialAdManager.kt
git commit -m "Implement AdMobInterstitialAdManager"
```

---

### Task 14: `AdMobAppOpenAdManager`

**Files:**
- Create: `adflow-admob/src/main/java/com/adflow/admob/fullscreen/AdMobAppOpenAdManager.kt`

**Interfaces:**
- Consumes: same as Task 13, plus `com.google.android.gms.ads.appopen.AppOpenAd`.
- Produces: `AdMobAppOpenAdManager(context, config) : AppOpenAdManager` — consumed by `AdMobProvider` (Task 18).

- [ ] **Step 1: Implement `AdMobAppOpenAdManager.kt`**

```kotlin
package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.AppOpenAdManager
import com.adflow.core.FullScreenAdManagerBase
import com.adflow.core.PlacementConfig
import com.adflow.core.ShowCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

class AdMobAppOpenAdManager(
    private val context: Context,
    config: PlacementConfig,
) : FullScreenAdManagerBase<AppOpenAd>(config, AdType.APP_OPEN), AppOpenAdManager {

    private val placementId = config.placementId

    override fun requestAd(adUnitId: String, onResult: (Result<AppOpenAd>) -> Unit) {
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    ad.onPaidEventListener = com.google.android.gms.ads.OnPaidEventListener { adValue ->
                        AdFlowCore.dispatchRevenue(
                            AdRevenueEvent(
                                placementId = placementId,
                                adType = AdType.APP_OPEN,
                                adUnitId = adUnitId,
                                valueMicros = adValue.valueMicros,
                                currencyCode = adValue.currencyCode,
                                precision = adValue.precisionType.toString(),
                                adNetwork = ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                            ),
                        )
                    }
                    onResult(Result.success(ad))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    onResult(Result.failure(RuntimeException(error.message)))
                }
            },
        )
    }

    override fun performShow(ad: AppOpenAd, activity: Activity, callback: ShowCallback) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()
            override fun onAdDismissedFullScreenContent() = callback.onAdDismissed()
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                callback.onAdFailedToShow(AdFlowError(error.code, error.message))
        }
        ad.show(activity)
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :adflow-admob:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add adflow-admob/src/main/java/com/adflow/admob/fullscreen/AdMobAppOpenAdManager.kt
git commit -m "Implement AdMobAppOpenAdManager"
```

---

### Task 15: `AdMobRewardedAdManager`

**Files:**
- Create: `adflow-admob/src/main/java/com/adflow/admob/fullscreen/AdMobRewardedAdManager.kt`

**Interfaces:**
- Consumes: `com.google.android.gms.ads.rewarded.RewardedAd`, `RewardedAdCallback`, `RewardItem` (`:adflow-core`).
- Produces: `AdMobRewardedAdManager(context, config) : RewardedAdManager` — consumed by `AdMobProvider` (Task 18). Note this class implements `RewardedAdManager` directly (not via `FullScreenAdManagerBase<TAd>`, since `RewardedAdManager.show` takes a `RewardedAdCallback`, not `ShowCallback`), but still reuses `WaterfallLoader`/`RetryPolicy`/`AdShowIntervalPolicy` internally for consistency with the other full-screen managers.

- [ ] **Step 1: Implement `AdMobRewardedAdManager.kt`**

```kotlin
package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdLoadResult
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.PlacementConfig
import com.adflow.core.RewardItem
import com.adflow.core.RewardedAdCallback
import com.adflow.core.RewardedAdManager
import com.adflow.core.WaterfallLoader
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdMobRewardedAdManager(
    private val context: Context,
    private val config: PlacementConfig,
) : RewardedAdManager {

    private var cachedAd: RewardedAd? = null
    private var loadedAtMs: Long = 0L
    private var retryAttempt: Int = 0
    private var isLoading: Boolean = false

    override fun isReady(): Boolean =
        cachedAd != null && System.currentTimeMillis() - loadedAtMs < config.expiryMs

    override fun load(onResult: (AdLoadResult) -> Unit) {
        if (!config.enabled) {
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
            return
        }
        if (config.loadRule?.isAllowed(config.placementId) == false) {
            onResult(AdLoadResult.Failure(AdFlowError(-2, "load rule rejected")))
            return
        }
        if (isLoading) return
        isLoading = true
        retryAttempt = 0
        startWaterfall(onResult)
    }

    private fun startWaterfall(onResult: (AdLoadResult) -> Unit) {
        AdFlowCore.logger.log(config.placementId, AdType.REWARDED, com.adflow.core.AdFlowEvent.LOADING)
        WaterfallLoader<RewardedAd>(config.adUnitIds) { adUnitId, cb -> requestAd(adUnitId, cb) }.start { result ->
            result.fold(
                onSuccess = { ad ->
                    cachedAd = ad
                    loadedAtMs = System.currentTimeMillis()
                    isLoading = false
                    AdFlowCore.logger.log(config.placementId, AdType.REWARDED, com.adflow.core.AdFlowEvent.LOADED)
                    onResult(AdLoadResult.Success)
                },
                onFailure = { error ->
                    retryAttempt += 1
                    if (retryAttempt > config.retryPolicy.maxRetries) {
                        isLoading = false
                        onResult(AdLoadResult.Failure(AdFlowError(-3, error.message ?: "waterfall exhausted")))
                        return@fold
                    }
                    val delayMs = config.retryPolicy.delayForAttempt(retryAttempt)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ startWaterfall(onResult) }, delayMs)
                },
            )
        }
    }

    private fun requestAd(adUnitId: String, onResult: (Result<RewardedAd>) -> Unit) {
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.onPaidEventListener = com.google.android.gms.ads.OnPaidEventListener { adValue ->
                        AdFlowCore.dispatchRevenue(
                            AdRevenueEvent(
                                placementId = config.placementId,
                                adType = AdType.REWARDED,
                                adUnitId = adUnitId,
                                valueMicros = adValue.valueMicros,
                                currencyCode = adValue.currencyCode,
                                precision = adValue.precisionType.toString(),
                                adNetwork = ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                            ),
                        )
                    }
                    onResult(Result.success(ad))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    onResult(Result.failure(RuntimeException(error.message)))
                }
            },
        )
    }

    override fun show(activity: Activity, callback: RewardedAdCallback) {
        if (!isReady()) {
            callback.onShowBlocked(BlockReason.NOT_READY)
            return
        }
        if (config.showRule?.isAllowed(config.placementId) == false) {
            callback.onShowBlocked(BlockReason.RULE_REJECTED)
            return
        }
        val ad = cachedAd ?: return
        cachedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()
            override fun onAdDismissedFullScreenContent() = callback.onAdDismissed()
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                callback.onAdFailedToShow(AdFlowError(error.code, error.message))
        }
        ad.show(activity) { rewardItem ->
            callback.onUserEarnedReward(RewardItem(rewardItem.type, rewardItem.amount))
        }
        if (config.preloadEnabled) load()
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :adflow-admob:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add adflow-admob/src/main/java/com/adflow/admob/fullscreen/AdMobRewardedAdManager.kt
git commit -m "Implement AdMobRewardedAdManager with reward/expiry callbacks"
```

---

### Task 16: `AdMobBannerAdManager`

**Files:**
- Create: `adflow-admob/src/main/java/com/adflow/admob/banner/AdMobBannerAdManager.kt`

**Interfaces:**
- Consumes: `BannerAdManager`, `WaterfallLoader`, `PlacementConfig`, `AdFlowCore`, `AdRevenueEvent`, `AdType` (`:adflow-core`); `com.google.android.gms.ads.AdView`.
- Produces: `AdMobBannerAdManager(context, config) : BannerAdManager` — consumed by `AdMobProvider` (Task 18).

- [ ] **Step 1: Implement `AdMobBannerAdManager.kt`**

```kotlin
package com.adflow.admob.banner

import android.content.Context
import android.view.View
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdLoadResult
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.BannerAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.WaterfallLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener

class AdMobBannerAdManager(
    private val context: Context,
    private val config: PlacementConfig,
) : BannerAdManager {

    private var adView: AdView? = null

    override fun isReady(): Boolean = adView != null

    override fun load(onResult: (AdLoadResult) -> Unit) {
        if (!config.enabled) {
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
            return
        }
        if (config.loadRule?.isAllowed(config.placementId) == false) {
            onResult(AdLoadResult.Failure(AdFlowError(-2, "load rule rejected")))
            return
        }
        WaterfallLoader<AdView>(config.adUnitIds) { adUnitId, cb -> requestAd(adUnitId, cb) }.start { result ->
            result.fold(
                onSuccess = {
                    adView = it
                    onResult(AdLoadResult.Success)
                },
                onFailure = { error ->
                    onResult(AdLoadResult.Failure(AdFlowError(-3, error.message ?: "no fill")))
                },
            )
        }
    }

    private fun requestAd(adUnitId: String, onResult: (Result<AdView>) -> Unit) {
        val view = AdView(context)
        view.setAdSize(AdSize.BANNER)
        view.adUnitId = adUnitId
        view.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                onResult(Result.success(view))
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                onResult(Result.failure(RuntimeException(error.message)))
            }
        }
        view.onPaidEventListener = OnPaidEventListener { adValue ->
            AdFlowCore.dispatchRevenue(
                AdRevenueEvent(
                    placementId = config.placementId,
                    adType = AdType.BANNER,
                    adUnitId = adUnitId,
                    valueMicros = adValue.valueMicros,
                    currencyCode = adValue.currencyCode,
                    precision = adValue.precisionType.toString(),
                    adNetwork = view.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                ),
            )
        }
        view.loadAd(AdRequest.Builder().build())
    }

    override fun getView(context: Context): View =
        adView ?: throw IllegalStateException("Banner for '${config.placementId}' has not loaded yet")
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :adflow-admob:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add adflow-admob/src/main/java/com/adflow/admob/banner/AdMobBannerAdManager.kt
git commit -m "Implement AdMobBannerAdManager"
```

---

### Task 17: `AdMobNativeAdManager` + default renderers

**Files:**
- Create: `adflow-admob/src/main/java/com/adflow/admob/nativead/AdMobNativeAdManager.kt`
- Create: `adflow-admob/src/main/java/com/adflow/admob/nativead/DefaultSmallNativeAdRenderer.kt`
- Create: `adflow-admob/src/main/java/com/adflow/admob/nativead/DefaultMediumNativeAdRenderer.kt`

**Interfaces:**
- Consumes: `NativeAdManager`, `NativeAdRenderer`, `NativeAdAssets`, `WaterfallLoader` (`:adflow-core`); `com.google.android.gms.ads.nativead.*`.
- Produces: `AdMobNativeAdManager(context, config) : NativeAdManager`, `DefaultSmallNativeAdRenderer`, `DefaultMediumNativeAdRenderer` — consumed by `AdMobProvider` (Task 18) and the demo app (Task 22).

- [ ] **Step 1: Implement `AdMobNativeAdManager.kt`**

```kotlin
package com.adflow.admob.nativead

import android.content.Context
import android.view.View
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdLoadResult
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.NativeAdAssets
import com.adflow.core.NativeAdManager
import com.adflow.core.NativeAdRenderer
import com.adflow.core.PlacementConfig
import com.adflow.core.WaterfallLoader
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

class AdMobNativeAdManager(
    private val context: Context,
    private val config: PlacementConfig,
) : NativeAdManager {

    private var cachedAd: NativeAd? = null

    override fun isReady(): Boolean = cachedAd != null

    override fun load(onResult: (AdLoadResult) -> Unit) {
        if (!config.enabled) {
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
            return
        }
        if (config.loadRule?.isAllowed(config.placementId) == false) {
            onResult(AdLoadResult.Failure(AdFlowError(-2, "load rule rejected")))
            return
        }
        WaterfallLoader<NativeAd>(config.adUnitIds) { adUnitId, cb -> requestAd(adUnitId, cb) }.start { result ->
            result.fold(
                onSuccess = {
                    cachedAd = it
                    onResult(AdLoadResult.Success)
                },
                onFailure = { error ->
                    onResult(AdLoadResult.Failure(AdFlowError(-3, error.message ?: "no fill")))
                },
            )
        }
    }

    private fun requestAd(adUnitId: String, onResult: (Result<NativeAd>) -> Unit) {
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                nativeAd.setOnPaidEventListener { adValue ->
                    AdFlowCore.dispatchRevenue(
                        AdRevenueEvent(
                            placementId = config.placementId,
                            adType = AdType.NATIVE,
                            adUnitId = adUnitId,
                            valueMicros = adValue.valueMicros,
                            currencyCode = adValue.currencyCode,
                            precision = adValue.precisionType.toString(),
                            adNetwork = nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                        ),
                    )
                }
                onResult(Result.success(nativeAd))
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    onResult(Result.failure(RuntimeException(error.message)))
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    override fun createView(context: Context, renderer: NativeAdRenderer): View {
        val ad = cachedAd ?: throw IllegalStateException("Native ad for '${config.placementId}' has not loaded yet")
        val view = renderer.createView(context)
        val assets = NativeAdAssets(
            headline = ad.headline.orEmpty(),
            body = ad.body,
            iconUri = ad.icon?.uri?.toString(),
            callToAction = ad.callToAction,
            starRating = ad.starRating,
            advertiser = ad.advertiser,
            mediaViewSlot = { ctx -> com.google.android.gms.ads.nativead.MediaView(ctx) },
        )
        renderer.bind(view, assets)
        if (view is NativeAdView) {
            view.setNativeAd(ad)
        }
        return view
    }
}
```

- [ ] **Step 2: Implement `DefaultSmallNativeAdRenderer.kt`**

```kotlin
package com.adflow.admob.nativead

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.adflow.core.NativeAdAssets
import com.adflow.core.NativeAdRenderer
import com.google.android.gms.ads.nativead.NativeAdView

class DefaultSmallNativeAdRenderer : NativeAdRenderer {

    override fun createView(context: Context): View {
        val headline = TextView(context).apply { id = View.generateViewId() }
        val body = TextView(context).apply { id = View.generateViewId() }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(headline)
            addView(body)
        }
        return NativeAdView(context).apply {
            addView(container)
            headlineView = headline
            bodyView = body
        }
    }

    override fun bind(view: View, assets: NativeAdAssets) {
        val adView = view as NativeAdView
        (adView.headlineView as TextView).text = assets.headline
        (adView.bodyView as TextView).text = assets.body.orEmpty()
    }
}
```

- [ ] **Step 3: Implement `DefaultMediumNativeAdRenderer.kt`**

```kotlin
package com.adflow.admob.nativead

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.adflow.core.NativeAdAssets
import com.adflow.core.NativeAdRenderer
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView

class DefaultMediumNativeAdRenderer : NativeAdRenderer {

    override fun createView(context: Context): View {
        val headline = TextView(context).apply { id = View.generateViewId() }
        val body = TextView(context).apply { id = View.generateViewId() }
        val media = MediaView(context).apply { id = View.generateViewId() }
        val cta = Button(context).apply { id = View.generateViewId() }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(headline)
            addView(media)
            addView(body)
            addView(cta)
        }
        return NativeAdView(context).apply {
            addView(container)
            headlineView = headline
            bodyView = body
            mediaView = media
            callToActionView = cta
        }
    }

    override fun bind(view: View, assets: NativeAdAssets) {
        val adView = view as NativeAdView
        (adView.headlineView as TextView).text = assets.headline
        (adView.bodyView as TextView).text = assets.body.orEmpty()
        (adView.callToActionView as Button).text = assets.callToAction.orEmpty()
    }
}
```

- [ ] **Step 4: Verify the module compiles**

Run: `./gradlew :adflow-admob:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add adflow-admob/src/main/java/com/adflow/admob/nativead
git commit -m "Implement AdMobNativeAdManager with small/medium default renderers"
```

---

### Task 18: `AdMobProvider`

**Files:**
- Create: `adflow-admob/src/main/java/com/adflow/admob/AdMobProvider.kt`

**Interfaces:**
- Consumes: `AdNetworkProvider` (Task 11), every `AdMob*Manager` from Tasks 13-17.
- Produces: `AdMobProvider(context: Context) : AdNetworkProvider` — this is what the demo app (Task 20) instantiates.

- [ ] **Step 1: Implement `AdMobProvider.kt`**

```kotlin
package com.adflow.admob

import android.content.Context
import com.adflow.admob.banner.AdMobBannerAdManager
import com.adflow.admob.fullscreen.AdMobAppOpenAdManager
import com.adflow.admob.fullscreen.AdMobInterstitialAdManager
import com.adflow.admob.fullscreen.AdMobRewardedAdManager
import com.adflow.admob.nativead.AdMobNativeAdManager
import com.adflow.core.AdNetworkProvider
import com.adflow.core.AppOpenAdManager
import com.adflow.core.BannerAdManager
import com.adflow.core.InterstitialAdManager
import com.adflow.core.NativeAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.RewardedAdManager
import com.google.android.gms.ads.MobileAds

class AdMobProvider(private val context: Context) : AdNetworkProvider {

    override fun initialize(context: Context, onComplete: () -> Unit) {
        MobileAds.initialize(context) { onComplete() }
    }

    override fun createInterstitial(config: PlacementConfig): InterstitialAdManager =
        AdMobInterstitialAdManager(context, config)

    override fun createAppOpen(config: PlacementConfig): AppOpenAdManager =
        AdMobAppOpenAdManager(context, config)

    override fun createRewarded(config: PlacementConfig): RewardedAdManager =
        AdMobRewardedAdManager(context, config)

    override fun createNative(config: PlacementConfig): NativeAdManager =
        AdMobNativeAdManager(context, config)

    override fun createBanner(config: PlacementConfig): BannerAdManager =
        AdMobBannerAdManager(context, config)
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :adflow-admob:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add adflow-admob/src/main/java/com/adflow/admob/AdMobProvider.kt
git commit -m "Implement AdMobProvider wiring all five ad managers"
```

---

### Task 19: Compose wrappers (`BannerAdView`, `NativeAdView`)

**Files:**
- Create: `adflow-admob/src/main/java/com/adflow/admob/banner/compose/BannerAdView.kt`
- Create: `adflow-admob/src/main/java/com/adflow/admob/nativead/compose/NativeAdView.kt`

**Interfaces:**
- Consumes: `BannerAdManager`, `NativeAdManager`, `NativeAdRenderer` (`:adflow-core`); `androidx.compose.ui.viewinterop.AndroidView`.
- Produces: `@Composable fun BannerAdView(manager: BannerAdManager, modifier: Modifier)`, `@Composable fun NativeAdView(manager: NativeAdManager, renderer: NativeAdRenderer, modifier: Modifier)` — consumed by the demo app's `HomeScreen` (Task 22).

- [ ] **Step 1: Implement `BannerAdView.kt`**

```kotlin
package com.adflow.admob.banner.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adflow.core.BannerAdManager

@Composable
fun BannerAdView(manager: BannerAdManager, modifier: Modifier = Modifier.fillMaxWidth()) {
    if (!manager.isReady()) return
    AndroidView(factory = { context -> manager.getView(context) }, modifier = modifier)
}
```

- [ ] **Step 2: Implement `NativeAdView.kt`**

```kotlin
package com.adflow.admob.nativead.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.core.NativeAdManager
import com.adflow.core.NativeAdRenderer

@Composable
fun NativeAdView(
    manager: NativeAdManager,
    renderer: NativeAdRenderer = DefaultMediumNativeAdRenderer(),
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    if (!manager.isReady()) return
    AndroidView(factory = { context -> manager.createView(context, renderer) }, modifier = modifier)
}
```

- [ ] **Step 3: Verify the module compiles**

Run: `./gradlew :adflow-admob:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add adflow-admob/src/main/java/com/adflow/admob/banner/compose/BannerAdView.kt \
        adflow-admob/src/main/java/com/adflow/admob/nativead/compose/NativeAdView.kt
git commit -m "Add Compose wrappers for Banner and Native ad views"
```

---

### Task 20: Demo app — manifest, Application class, and placement wiring

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/dev/adflow/AdFlowDemoApp.kt`
- Create: `app/src/main/java/com/dev/adflow/DemoAdPlacements.kt`
- Create: `app/src/main/java/com/dev/adflow/PremiumState.kt`

**Interfaces:**
- Consumes: `AdMobProvider` (Task 18), `AdFlowCore`, `PlacementConfig`, `AdRule` (`:adflow-core`).
- Produces: `AdFlowDemoApp` (registered as `android:name` in the manifest), `DemoAdPlacements` object exposing `splashInterstitial`, `globalInterstitial`, `rewarded`, `appOpen`, `banner`, `native` manager instances — consumed by `SplashScreen`/`HomeScreen` (Tasks 21-22). `PremiumState` — a trivial in-memory toggle backing the demo `AdRule`, consumed by `HomeScreen`.

Uses Google's official AdMob test App ID and test ad unit IDs (safe to ship, documented by Google for testing).

- [ ] **Step 1: Add the test AdMob App ID and Application name to the manifest**

Edit `app/src/main/AndroidManifest.xml`, add inside `<application>` (before the `<activity>` block) and set `android:name`:

```xml
    <application
        android:name=".AdFlowDemoApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AdFlow">
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713" />
```

(Leave the existing `<activity>` block below it unchanged.)

- [ ] **Step 2: Create `PremiumState.kt`**

```kotlin
package com.dev.adflow

object PremiumState {
    var isPremium: Boolean = false
}
```

- [ ] **Step 3: Create `DemoAdPlacements.kt`**

```kotlin
package com.dev.adflow

import android.content.Context
import com.adflow.admob.AdMobProvider
import com.adflow.core.AdNetworkProvider
import com.adflow.core.AdRule
import com.adflow.core.AppOpenAdManager
import com.adflow.core.BannerAdManager
import com.adflow.core.InterstitialAdManager
import com.adflow.core.NativeAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.RewardedAdManager

class DemoAdPlacements(context: Context) {

    // The single line that ties the app to a network implementation; swap
    // AdMobProvider for another AdNetworkProvider implementation to switch networks.
    val provider: AdNetworkProvider = AdMobProvider(context)

    private val notPremium = AdRule { !PremiumState.isPremium }

    val splashInterstitial: InterstitialAdManager = provider.createInterstitial(
        PlacementConfig(
            placementId = "splash_interstitial",
            adUnitIds = listOf("ca-app-pub-3940256099942544/1033173712"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )

    val globalInterstitial: InterstitialAdManager = provider.createInterstitial(
        PlacementConfig(
            placementId = "global_interstitial",
            adUnitIds = listOf("ca-app-pub-3940256099942544/1033173712"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )

    val appOpen: AppOpenAdManager = provider.createAppOpen(
        PlacementConfig(
            placementId = "app_open",
            adUnitIds = listOf("ca-app-pub-3940256099942544/9257395921"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )

    val rewarded: RewardedAdManager = provider.createRewarded(
        PlacementConfig(
            placementId = "rewarded",
            adUnitIds = listOf("ca-app-pub-3940256099942544/5224354917"),
        ),
    )

    val banner: BannerAdManager = provider.createBanner(
        PlacementConfig(
            placementId = "home_banner",
            adUnitIds = listOf("ca-app-pub-3940256099942544/9214589741"),
            loadRule = notPremium,
        ),
    )

    val native: NativeAdManager = provider.createNative(
        PlacementConfig(
            placementId = "home_native",
            adUnitIds = listOf("ca-app-pub-3940256099942544/2247696110"),
            loadRule = notPremium,
        ),
    )
}
```

- [ ] **Step 4: Create `AdFlowDemoApp.kt`**

```kotlin
package com.dev.adflow

import android.app.Application
import com.adflow.core.AdFlowCore
import com.adflow.core.LogcatAdFlowLogger

class AdFlowDemoApp : Application() {

    lateinit var placements: DemoAdPlacements
        private set

    override fun onCreate() {
        super.onCreate()
        AdFlowCore.configure(logger = LogcatAdFlowLogger(tag = "AdFlow"))
        placements = DemoAdPlacements(this)
        placements.provider.initialize(this) {
            placements.splashInterstitial.load()
            placements.globalInterstitial.load()
            placements.appOpen.load()
            placements.rewarded.load()
            placements.banner.load()
            placements.native.load()
        }
    }
}
```

- [ ] **Step 5: Verify the app module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml \
        app/src/main/java/com/dev/adflow/AdFlowDemoApp.kt \
        app/src/main/java/com/dev/adflow/DemoAdPlacements.kt \
        app/src/main/java/com/dev/adflow/PremiumState.kt
git commit -m "Wire demo app Application class and ad placements via AdMobProvider"
```

---

### Task 21: Demo app — Splash screen

**Files:**
- Create: `app/src/main/java/com/dev/adflow/SplashScreen.kt`
- Modify: `app/src/main/java/com/dev/adflow/MainActivity.kt`

**Interfaces:**
- Consumes: `DemoAdPlacements.splashInterstitial` (Task 20), `ShowCallback` (`:adflow-core`).
- Produces: `@Composable fun SplashScreen(onDone: () -> Unit)`; `MainActivity` now shows `SplashScreen` first, then `HomeScreen` (Task 22).

- [ ] **Step 1: Create `SplashScreen.kt`**

```kotlin
package com.dev.adflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.adflow.core.AdFlowError
import com.adflow.core.BlockReason
import com.adflow.core.ShowCallback

@Composable
fun SplashScreen(placements: DemoAdPlacements, onDone: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect onDone()
        placements.splashInterstitial.show(activity, object : ShowCallback {
            override fun onAdDismissed() = onDone()
            override fun onAdFailedToShow(error: AdFlowError) = onDone()
            override fun onShowBlocked(reason: BlockReason) = onDone()
        })
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
        Text(text = "AdFlow demo", modifier = Modifier)
    }
}
```

- [ ] **Step 2: Read the current `MainActivity.kt`**

Run: `cat app/src/main/java/com/dev/adflow/MainActivity.kt`

- [ ] **Step 3: Update `MainActivity.kt` to navigate Splash -> Home**

Replace its `setContent` body so that it tracks a simple `showSplash` boolean state and renders `SplashScreen` then `HomeScreen`:

```kotlin
package com.dev.adflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dev.adflow.ui.theme.AdFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val placements = (application as AdFlowDemoApp).placements
        setContent {
            AdFlowTheme {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    SplashScreen(placements = placements, onDone = { showSplash = false })
                } else {
                    HomeScreen(placements = placements)
                }
            }
        }
    }
}
```

(This references `HomeScreen`, created in Task 22 — the app will not compile until that task lands; that is expected and resolved by the next task.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dev/adflow/SplashScreen.kt \
        app/src/main/java/com/dev/adflow/MainActivity.kt
git commit -m "Add SplashScreen and wire MainActivity navigation to Home"
```

---

### Task 22: Demo app — Home screen

**Files:**
- Create: `app/src/main/java/com/dev/adflow/HomeScreen.kt`

**Interfaces:**
- Consumes: `DemoAdPlacements` (Task 20), `BannerAdView`/`NativeAdView` Composables (Task 19), `RewardedAdCallback` (`:adflow-core`).
- Produces: `@Composable fun HomeScreen(placements: DemoAdPlacements)` — completes the demo app and makes `MainActivity` (Task 21) compile.

- [ ] **Step 1: Create `HomeScreen.kt`**

```kotlin
package com.dev.adflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adflow.admob.banner.compose.BannerAdView
import com.adflow.admob.nativead.compose.NativeAdView
import com.adflow.core.RewardItem
import com.adflow.core.RewardedAdCallback
import com.adflow.core.ShowCallback

@Composable
fun HomeScreen(placements: DemoAdPlacements) {
    val context = LocalContext.current
    var premium by remember { mutableStateOf(PremiumState.isPremium) }
    var lastReward by remember { mutableStateOf<RewardItem?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row {
            Text("Premium (disable ads)")
            Switch(checked = premium, onCheckedChange = {
                premium = it
                PremiumState.isPremium = it
            })
        }

        Button(onClick = {
            (context as? android.app.Activity)?.let { placements.globalInterstitial.show(it, ShowCallback.NONE) }
        }) { Text("Show Global Interstitial") }

        Button(onClick = {
            (context as? android.app.Activity)?.let {
                placements.rewarded.show(it, object : RewardedAdCallback {
                    override fun onUserEarnedReward(reward: RewardItem) { lastReward = reward }
                })
            }
        }) { Text("Show Rewarded Ad") }

        Text("Last reward: ${lastReward?.let { "${it.amount} ${it.type}" } ?: "none yet"}")

        Button(onClick = {
            (context as? android.app.Activity)?.let { placements.appOpen.show(it, ShowCallback.NONE) }
        }) { Text("Show App Open Ad") }

        NativeAdView(manager = placements.native)
        BannerAdView(manager = placements.banner)
    }
}
```

- [ ] **Step 2: Verify the whole app builds**

Run: `./gradlew :app:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dev/adflow/HomeScreen.kt
git commit -m "Add HomeScreen exercising every ad type from the demo app"
```

---

### Task 23: Full-project verification and manual smoke test

**Files:** none (verification only).

- [ ] **Step 1: Run every unit test in the project**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL` — all `:adflow-core` tests from Tasks 3-10 pass.

- [ ] **Step 2: Assemble the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual smoke test on a device/emulator**

Install and launch the app (`./gradlew :app:installDebug`, then start it from the launcher, or via `adb shell am start -n com.dev.adflow/.MainActivity`). Using `adb logcat -s AdFlow`, confirm:
- Splash Interstitial: `LOADING` → `LOADED` → `SHOWN` events appear, then the Home screen appears after dismissal.
- Toggling "Premium" on and tapping "Show Global Interstitial" logs `SHOW_BLOCKED` (via the `notPremium` `AdRule`), and no ad appears.
- Toggling "Premium" off, "Show Global Interstitial" shows a test interstitial.
- "Show Rewarded Ad" shows a rewarded ad; after watching it, "Last reward" updates.
- "Show App Open Ad" shows a test app-open ad (subject to the show-interval gap versus the interstitial just shown — try again if blocked).
- The Native card and Banner render at the bottom of the Home screen.

- [ ] **Step 4: Fix forward on any discrepancy**

If a real AdMob API signature differs from what Tasks 13-19 assumed (SDK versions do drift), fix the specific call in that file, re-run `:adflow-admob:compileDebugKotlin`, and commit the fix with a message describing the corrected API call.

---

## Self-Review Notes

- **Spec coverage:** Every §3-§7 item in the design spec maps to a task — module split (1-2), `PlacementConfig`/`AdRule`/`RetryPolicy` (4), waterfall (5), show-interval policy (6), logging/revenue/`AdFlowCore` (7), full-screen orchestration + typed interfaces (8-9), Native/Banner contracts (10), `AdNetworkProvider` (11), AdMob implementations (13-18), Compose wrappers (19), demo app (20-22), manual verification (23).
- **Type consistency:** `AdType` is the single enum used from Task 3 onward (no second `FullScreenAdType`). `FullScreenAdManagerBase` constructor signature (`config, adType`) in Task 8 matches every AdMob subclass call in Tasks 13-14. `RewardedAdManager.show(activity, RewardedAdCallback)` (Task 9) matches `AdMobRewardedAdManager.show` (Task 15) and `HomeScreen`'s call (Task 22).
- **No placeholders:** every step has complete, runnable code; the one open unknown (exact `play-services-ads` version) is resolved by a deterministic lookup command in Task 2, not left as "TBD".
