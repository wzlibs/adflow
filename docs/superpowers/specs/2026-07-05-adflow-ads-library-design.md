# AdFlow Ads Library — Design Spec

Date: 2026-07-05
Status: Approved for planning

## 1. Goal

Build a reusable Android ad library supporting Interstitial, App Open, Rewarded, Native,
and Banner ads on top of AdMob today, designed so that:

- Multiple independent instances of the same ad type can exist per app (e.g. a Splash-only
  Interstitial vs. an app-wide global Interstitial), each with its own config and Ad Unit IDs.
- Each placement supports a waterfall of Ad Unit IDs, incremental-backoff retry on load
  failure, optional preload, and app-supplied policies gating load/show (remove-ads,
  premium, business cooldowns, etc.).
- Cross-cutting show-frequency capping between Interstitial and App Open is automatic.
- Ad revenue (AdMob paid event) can be forwarded to Adjust (or any tracker) via a pluggable
  interface, with no hard dependency on Adjust SDK.
- The ad network itself (AdMob today) can be swapped later (e.g. AppLovin MAX, or a second
  AdMob-based implementation written from scratch) **without changing any app/client code**,
  by introducing a core-contract / network-adapter module split.
- Native/Banner rendering works for both Compose-based and XML-based host apps, with a
  default template and a fully custom option.

Explicitly out of scope for this spec: Flutter support, remote/JSON-based config source,
an actual second network adapter (MAX, etc.) — the architecture must not preclude these,
but none are implemented now.

## 2. Module Structure

Three Gradle modules:

| Module | Package | Depends on | Purpose |
|---|---|---|---|
| `:adflow-core` | `com.adflow.core` | AndroidX only (no ad SDK) | Network-agnostic contracts, orchestration logic, and value types. This is what client/app code programs against. |
| `:adflow-admob` | `com.adflow.admob` | `:adflow-core`, `play-services-ads` | Concrete AdMob implementation of the core contracts. |
| `:app` | `com.dev.adflow` | `:adflow-core`, `:adflow-admob` | Demo app exercising every ad type via the library. |

Future network adapters (e.g. `:adflow-max`, `:adflow-admob-v2`) are added as new sibling
modules implementing `:adflow-core` contracts; they do not depend on `:adflow-admob` and
`:adflow-admob` does not depend on them. The app depends on exactly one network adapter
module at a time, selected in one place (see §3.7).

## 3. `:adflow-core` Contracts

### 3.1 Placement configuration

```kotlin
data class PlacementConfig(
    val placementId: String,
    val enabled: Boolean = true,
    val preloadEnabled: Boolean = true,
    val adUnitIds: List<String>,          // waterfall order
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    val loadRule: AdRule? = null,
    val showRule: AdRule? = null,
    val expiryMs: Long = 4 * 60 * 60 * 1000L, // AdMob-recommended default: 4h
)

fun interface AdRule {
    fun isAllowed(placementId: String): Boolean
}
```

`expiryMs` is only consulted by `FullScreenAdManagerBase.isReady()` (Interstitial/App
Open/Rewarded — AdMob documents ~4h staleness for these). Native and Banner managers
ignore it: Banner has no "loaded ad object" that goes stale the same way, and Native ad
objects are treated as valid until explicitly reloaded.

`AdRule` is how the host app blocks load/show without touching library internals: e.g. a
`RemoveAdsRule` returning `false` when the user purchased remove-ads, or a rule that checks
a cooldown timestamp set after a Rewarded Ad completes.

### 3.2 Retry policy (incremental backoff)

```kotlin
data class RetryPolicy(
    val initialDelayMs: Long = 5_000,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = 60_000,
    val maxRetries: Int = 5,
) {
    companion object { val DEFAULT = RetryPolicy() }
    fun delayForAttempt(attempt: Int): Long =
        (initialDelayMs * Math.pow(multiplier, (attempt - 1).toDouble()))
            .toLong().coerceAtMost(maxDelayMs)
}
```

With defaults this produces 5s, 10s, 20s, 40s, 60s (capped) — matching the example in
requirements. Fully configurable per placement.

### 3.3 Waterfall loader

```kotlin
class WaterfallLoader<TAd>(
    private val adUnitIds: List<String>,
    private val attemptLoad: (adUnitId: String, onResult: (Result<TAd>) -> Unit) -> Unit,
) {
    fun start(onFinalResult: (Result<TAd>) -> Unit)
    // Tries adUnitIds[0]; on failure tries adUnitIds[1]; ...; on exhausting the list,
    // calls onFinalResult(Result.failure(...)). Emits AdFlowEvent.WATERFALL_NEXT via the
    // logger between attempts.
}
```

### 3.4 Show-interval policy (Interstitial ↔ App Open frequency capping)

One `AdType` enum is used everywhere in `:adflow-core` (logging, revenue, base-class
tagging): `enum class AdType { INTERSTITIAL, APP_OPEN, REWARDED, NATIVE, BANNER }`.

```kotlin
data class ShowIntervalConfig(
    val interstitialAfterInterstitialMs: Long = 5_000,
    val appOpenAfterAppOpenMs: Long = 5_000,
    val interstitialAfterAppOpenMs: Long = 3_000,
    val appOpenAfterInterstitialMs: Long = 2_000,
)

internal object AdShowIntervalPolicy {
    fun configure(config: ShowIntervalConfig)
    fun canShow(type: AdType, now: Long = System.currentTimeMillis()): Boolean
    fun recordShown(type: AdType, now: Long = System.currentTimeMillis())
}
```

`canShow`/`recordShown` only enforce a gap for `INTERSTITIAL` and `APP_OPEN` (checking both
the same-type gap and the cross-type gap against whichever of the two was shown most
recently); for `REWARDED`, `NATIVE`, `BANNER` they are a no-op (`canShow` always `true`) —
frequency capping was only requested between Interstitial and App Open. Process-wide,
in-memory (gaps are seconds, so surviving process death is not a requirement). Every
`FullScreenAdManagerBase.show()` call consults `canShow` before displaying and calls
`recordShown` after a successful show — no app wiring needed. Configuration goes through
`AdFlowCore.configure(...)` (see §3.9), not this object directly.

### 3.5 Full-screen ad manager base (Interstitial / App Open / Rewarded)

```kotlin
interface FullScreenAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun show(activity: Activity, callback: ShowCallback = ShowCallback.NONE)
}

abstract class FullScreenAdManagerBase<TAd>(
    protected val config: PlacementConfig,
    protected val adType: AdType,   // INTERSTITIAL, APP_OPEN, or REWARDED
) : FullScreenAdManager {
    protected abstract fun requestAd(adUnitId: String, onResult: (Result<TAd>) -> Unit)
    protected abstract fun performShow(ad: TAd, activity: Activity, callback: ShowCallback)

    final override fun isReady(): Boolean       // cached ad present && not expired
    final override fun load(onResult) { /* enabled + loadRule -> WaterfallLoader -> RetryPolicy on failure */ }
    final override fun show(activity, callback) {
        /* showRule + AdShowIntervalPolicy.canShow -> performShow -> recordShown ->
           preload again if preloadEnabled */
    }
}
```

`InterstitialAdManager`, `AppOpenAdManager` extend this with a plain `ShowCallback`
(`onAdShown`, `onAdFailedToShow`, `onAdDismissed`, `onShowBlocked(reason)`). `RewardedAdManager`
extends it with the fuller `RewardedAdCallback`:

```kotlin
interface RewardedAdCallback {
    fun onAdLoaded() {}
    fun onAdFailedToLoad(error: AdFlowError) {}
    fun onAdShown() {}
    fun onAdFailedToShow(error: AdFlowError) {}
    fun onUserEarnedReward(reward: RewardItem) {}   // RewardItem: plain (type: String, amount: Int)
    fun onAdDismissed() {}
    fun onAdExpired() {}
    fun onShowBlocked(reason: BlockReason) {}
}
```

App Open ads are **not** auto-shown on process foreground by the library (multiple
instances could exist; auto-show would be ambiguous). The host app calls `show()` at the
right moment (e.g. `Application`/lifecycle observer it owns); `isReady()` plus
`AdShowIntervalPolicy` prevent stacking on top of a just-shown Interstitial.

### 3.6 Native & Banner

```kotlin
interface BannerAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun getView(context: Context): View   // XML use
}
// Compose: @Composable fun BannerAdView(manager: BannerAdManager, modifier: Modifier = Modifier)

data class NativeAdAssets(
    val headline: String,
    val body: String?,
    val iconUri: String?,
    val callToAction: String?,
    val starRating: Double?,
    val advertiser: String?,
    val mediaViewSlot: (Context) -> View,   // network supplies the actual media view
)

interface NativeAdRenderer {
    fun createView(context: Context): View
    fun bind(view: View, assets: NativeAdAssets)
}

interface NativeAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun createView(context: Context, renderer: NativeAdRenderer = defaultRenderer()): View
}
// Compose: @Composable fun NativeAdView(manager: NativeAdManager, renderer: NativeAdRenderer = ...)
```

Native/Banner do not participate in `AdShowIntervalPolicy` (not full-screen), but do use
`loadRule`/`showRule`, waterfall, retry, and preload like every other placement.

Renderer implementations (default small/medium templates, and the AdMob-specific view
wiring that registers click/impression tracking) live in `:adflow-admob`, since only the
network SDK knows how to bind its own ad object to views. `NativeAdAssets` and the
`NativeAdRenderer` interface stay network-agnostic in `:adflow-core`, so an app-authored
custom renderer's *shape* doesn't change when the network adapter is swapped — only the
concrete renderer instance passed in changes.

### 3.7 Network provider (the swap point)

```kotlin
interface AdNetworkProvider {
    fun initialize(context: Context, onComplete: () -> Unit = {})
    fun createInterstitial(config: PlacementConfig): InterstitialAdManager
    fun createAppOpen(config: PlacementConfig): AppOpenAdManager
    fun createRewarded(config: PlacementConfig): RewardedAdManager
    fun createNative(config: PlacementConfig): NativeAdManager
    fun createBanner(config: PlacementConfig): BannerAdManager
}
```

Exactly one line in the host app chooses the implementation:
`val adProvider: AdNetworkProvider = AdMobProvider(context)`. Everywhere else in the app
refers only to `:adflow-core` interfaces/types, never AdMob types directly. Swapping to
`:adflow-max` (future) means changing that one line and the module dependency; no other
app code changes.

### 3.8 Revenue tracking

```kotlin
data class AdRevenueEvent(
    val placementId: String,
    val adType: AdType,
    val adUnitId: String,
    val valueMicros: Long,
    val currencyCode: String,
    val precision: String,       // ESTIMATED / PUBLISHER_PROVIDED / PRECISE
    val adNetwork: String?,
)
interface RevenueLogger { fun onRevenuePaid(event: AdRevenueEvent) }
```

No logger registered ⇒ feature is a no-op. The host app supplies its own `RevenueLogger`
that calls `Adjust.trackAdRevenue(...)`; `:adflow-core` and `:adflow-admob` never depend on
the Adjust SDK. Registration goes through `AdFlowCore` (§3.9).

### 3.9 Logging, revenue registration & global config

```kotlin
enum class AdFlowEvent {
    LOADING, LOADED, LOAD_FAILED, RETRYING, WATERFALL_NEXT,
    SHOWN, SHOW_BLOCKED, SHOW_FAILED, NO_FILL, EXPIRED,
}
interface AdFlowLogger {
    fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String? = null)
}
class LogcatAdFlowLogger(private val tag: String = "AdFlow") : AdFlowLogger

object AdFlowCore {
    fun configure(
        showIntervalConfig: ShowIntervalConfig = ShowIntervalConfig(),
        logger: AdFlowLogger = LogcatAdFlowLogger(),
    )
    fun addRevenueLogger(logger: RevenueLogger)
    fun removeRevenueLogger(logger: RevenueLogger)
}
```

`AdFlowCore` is the single global configuration entry point (called once, e.g. from
`Application.onCreate`). It holds the active `AdFlowLogger`, the `ShowIntervalConfig`
(forwarded to `AdShowIntervalPolicy`), and the list of registered `RevenueLogger`s. All
managers read the current logger/config from `AdFlowCore` internally rather than taking
them as constructor parameters, so placements don't need to thread logging/config through
every call site. Default logger is `LogcatAdFlowLogger`; the host app can override it (e.g.
to forward into Crashlytics/Analytics) via `AdFlowCore.configure(logger = ...)`.

## 4. `:adflow-admob` Implementation

- `AdMobProvider : AdNetworkProvider` — creates concrete managers below and calls
  `MobileAds.initialize`.
- `AdMobInterstitialAdManager`, `AdMobAppOpenAdManager`, `AdMobRewardedAdManager` extend
  `FullScreenAdManagerBase`, implementing `requestAd`/`performShow` with the real AdMob
  APIs (`InterstitialAd.load`, `AppOpenAd.load`, `RewardedAd.load`, `FullScreenContentCallback`,
  `OnUserEarnedRewardListener`).
- `AdMobBannerAdManager` wraps a real `AdView`.
- `AdMobNativeAdManager` loads a real `NativeAd`; `DefaultSmallNativeAdRenderer` /
  `DefaultMediumNativeAdRenderer` wrap a real `NativeAdView`, binding `NativeAdAssets`
  into asset views via `setHeadlineView`/`setBodyView`/etc. so AdMob's click/impression
  tracking works.
- Each manager's paid-event listener converts AdMob's `AdValue` into `AdRevenueEvent` and
  forwards it to every `RevenueLogger` registered via `AdFlowCore.addRevenueLogger`.

A future `:adflow-admob-v2` or `:adflow-max` module can either extend
`FullScreenAdManagerBase` (reusing the retry/waterfall/show-interval orchestration) or
implement `FullScreenAdManager` directly from scratch — both are valid since `:adflow-core`
exposes the interface and the convenience base class separately.

## 5. Demo App (`:app`)

Uses Google's official AdMob test App ID and test Ad Unit IDs.

- `AdFlowDemoApp` (Application): calls `AdMobProvider.initialize`, constructs the demo
  manager instances (`splashInterstitial`, `globalInterstitial`, `rewarded`, `appOpen`,
  `banner`, `native`), demonstrating that the app owns instance lifecycle (no service
  locator in the library).
- `SplashScreen`: loads/shows the Splash-scoped Interstitial before navigating to Home.
- `HomeScreen`: buttons for Global Interstitial, Rewarded (shows reward result), App Open;
  a pinned Banner; a Native Ad card; a "Premium (disable ads)" switch demonstrating a live
  `AdRule` blocking load/show.
- All lifecycle events visible via `LogcatAdFlowLogger` under tag `AdFlow`.

## 6. Testing Strategy

Pure-JVM unit tests (no Robolectric needed) for network-agnostic logic in `:adflow-core`:
- `RetryPolicy.delayForAttempt` backoff sequence and cap.
- `WaterfallLoader` ordering/fallback using a fake `attemptLoad`.
- `AdShowIntervalPolicy.canShow` using injected/fake timestamps.
- `AdRule` combination/short-circuit behavior in `FullScreenAdManagerBase`.

Real AdMob load/show behavior in `:adflow-admob` is not unit-testable and is verified
manually through the `:app` demo on a device/emulator.

## 7. Explicitly Out of Scope (Future Work)

- Flutter bindings (Platform Channel) on top of `:adflow-core`.
- Remote/JSON-driven `PlacementConfig` (config is Kotlin-code-declared for now;
  `PlacementConfig` is a plain data class so a future remote source can construct it
  without an API change).
- A second network adapter module (`:adflow-max`, `:adflow-admob-v2`) — the module split
  in §2 exists specifically so this can be added later without touching `:adflow-core` or
  the app.
- A separate `-compose` artifact split from `:adflow-admob` (Compose wrappers currently
  live alongside the AdMob implementation since the demo app is Compose-only).
