## 2.2.0

* Bump the underlying `adflow-core`/`adflow-admob` dependency to `v1.0.0-alpha05`.
* Added `AdFlow.updateShowIntervalConfig()` - lets the app change the minimum gap between
  Interstitial/App Open after `AdFlow.initialize()` has already run (e.g. when the value comes
  from the app's own remote config and changes after init). Takes effect immediately on the next
  `canShow()`/`show()` check; does not reset any cooldown already in progress.

## 2.1.0

* Bump the underlying `adflow-core`/`adflow-admob` dependency to `v1.0.0-alpha04`.
* SDK init (`MobileAds.initialize()`) now depends only on consent (`canRequestAds()`), no longer
  waits for first-foreground - can run as early as `Application.onCreate()` if consent already
  resolved from a previous session.
* **Behavior change**: `preload = true` no longer auto-loads a placement during `AdFlow.initialize()`.
  The first `load()` for Interstitial/Rewarded must now always be app-driven; Banner/Native are
  unaffected (still self-load when their view attaches). `preload` still governs auto-loading the
  *next* ad after the current one is consumed (show/`release()`) - that part is unchanged.
  `autoShowOnForeground` (App Open) is unaffected end-to-end - it now also triggers its own
  `load()` when not ready, so it keeps working with no app-side change.
* Fix: `FullScreenAd.awaitReady()` (Interstitial/App Open/Rewarded) now triggers its own `load()`
  like the Dart-side contract already implied, instead of only waiting for an already-in-flight
  load. Previously, calling `awaitReady()` on a placement with `preload = false` (or before
  anything else had loaded it) would hang until timeout since nothing was requesting the ad.
* Fix: a `.load()` call blocked by `CONSENT_REQUIRED` (e.g. called before the UMP form resolves)
  is no longer silently dropped - it's now retried automatically once consent is granted, instead
  of requiring the app to listen for `onError`/blocked callbacks and retry manually.

## 2.0.0

* Added `AdFlowCollapsibleNative` - shows a native ad with a close button that switches the same
  slot to a banner ad (also triggered automatically if the native ad fails to load), composing
  `AdFlowNative`/`AdFlowBanner` directly with no separate platform view.
* **BREAKING**: removed the global `AdFlow.setAdsEnabled(bool)`. Restored the v1 per-placement
  `setEnabled(bool)` instead, now on every ad handle
  (`AdFlowInterstitialAd`/`AdFlowAppOpenAd`/`AdFlowRewardedAd`/`AdFlowBannerAd`/`AdFlowNativeAd`) -
  toggling one placement no longer forces every other placement off too, e.g. a "premium" toggle
  can leave a rewarded placement showing while disabling everything else. No `adflow-core` change
  needed - both the old global flag and the new per-placement map work by feeding
  `loadWhen`/`showWhen` (already-existing native `AdRule` hooks), just scoped differently.
* Added `onLoading`/`onLoaded`/`onError` to `AdFlowNative`/`AdFlowBanner` - side-effect callbacks
  (safe to call `setState()` inside them) that fire alongside the existing `loading`/`failed`
  widget builders, which run during Flutter's build phase and are not a safe place for side
  effects.
* Bump the underlying `adflow-core`/`adflow-admob` dependency to `v1.0.0-alpha03` (internal fix:
  `AppOpenForegroundObserver` now defers entirely to `AppOpenAd.canShow` instead of duplicating a
  subset of its checks - no API change).

## 1.0.0

* First stable release of the state-first rewrite (was published as `1.0.0-alpha.1`/`1.0.0-alpha.2`
  prereleases). No API change from `1.0.0-alpha.2` - see that entry below for what it added over
  `1.0.0-alpha.1`.

## 1.0.0-alpha.2

* Bump the underlying `adflow-core`/`adflow-admob` dependency to `v1.0.0-alpha02`.
* Added `canShow()` to `AdFlowInterstitialAd`/`AdFlowAppOpenAd`/`AdFlowRewardedAd` - a synchronous,
  side-effect-free query for whether calling `show()` right now would actually proceed (accounts
  for `showRule`, the minimum interval between full-screen shows, and whether another full-screen
  ad is currently showing, in addition to whether the ad is loaded). Not available on Banner/Native
  - those ad types have no such gates on the native side either.

## 1.0.0-alpha.1

* Breaking rewrite for AdFlow native `v1.0.0-alpha01`.
* Declare all placements once through `AdFlow.initialize`; removed v1 manager creation and
  per-placement `setEnabled` APIs.
* Added reactive `AdState` listenables, `awaitReady`, and unified full-screen show callbacks.
* Added self-managing `AdFlowBanner` and `AdFlowNative` widgets. Polling, blocked flags, generation
  keys, and manual native view rebinding are no longer required.
* Added global `AdFlow.setAdsEnabled`, state streaming through Pigeon, v2 block reasons, and v2
  native renderer support.

## 0.5.0

* Bump the underlying `adflow-core`/`adflow-admob` dependency to `v0.7.0`. `showRule` on a
  Native/Banner placement now actually blocks rendering (previously ignored - only
  Interstitial/App Open/Rewarded honored it). Both the "not loaded yet" and "showRule rejected"
  cases now report through `AdFlowNativeAdView.onShowBlocked`/`AdFlowBannerAdView.onShowBlocked`
  instead of one of them throwing, so a `showRule` rejection after an ad was already cached can no
  longer crash the app.
* `AdFlowNativeAdView`/`AdFlowBannerAdView` no longer need an `isReady`/readiness check before
  building the widget - call them directly and use `onShowBlocked` to hide/retry if blocked. See
  `example/lib/home_screen.dart` (`_retryWhileBlocked`) for the recommended pattern. No breaking
  API change - `onShowBlocked` is a new optional callback.

## 0.4.2

* Fix: `setEnabled(false)` only blocked `show()` for Interstitial/App Open/Rewarded, and had no
  effect at all on Banner/Native - `load()` kept fetching ads in the background for every ad type,
  and Banner/Native platform views kept rendering the cached ad regardless of `setEnabled()`. Now
  `setEnabled(false)` consistently blocks both `load()` and rendering for every ad type, so a fully
  "disabled" placement (e.g. a VIP/premium user) stops making ad requests entirely instead of just
  hiding the show/UI surface. No API change - same `setEnabled(bool)` call.

## 0.4.1

* Bump the underlying `adflow-core`/`adflow-admob` dependency to `v0.6.0`, which renders the native
  ad icon in `DefaultSmallNativeAdRenderer` (previously headline + body only, no image). No Dart
  API change.

## 0.4.0

* Custom native ad UI: register your own Kotlin `NativeAdRenderer` via
  `AdflowFlutterPlugin.registerNativeAdRenderer(flutterEngine, rendererId, renderer)` (typically in
  `MainActivity.configureFlutterEngine()`), then select it per-widget with
  `AdFlowNativeAdView(rendererId: 'id')`. `rendererId` is independent of `placementId`, so a single
  app can register several renderers and use different ones across different native ad placements.
  Falls back to `DefaultMediumNativeAdRenderer` (with a Logcat warning) if `rendererId` doesn't
  match a registered renderer, instead of crashing. See README "Hiển thị từng loại ad" → Native.

## 0.3.2

* Bump the underlying `adflow-core`/`adflow-admob` dependency to `v0.5.0`, which makes the default
  retry policy retry indefinitely on no-fill instead of giving up after 5 attempts (backoff still
  caps at 60s/attempt). A placement no longer gets permanently stuck failed for the rest of the
  session just because the ad network no-filled a few times in a row. No Dart API change.

## 0.3.1

* Fix: `show()` on `AdFlowInterstitialAd`/`AdFlowAppOpenAd`/`AdFlowRewardedAd` could hang forever
  (the returned `Future<void>` never completing) when called with no `Activity` currently attached
  (e.g. a screen rotation or the app briefly backgrounded between `load()` finishing and `show()`
  being called), or when the placement had no underlying native manager registered. Both cases now
  report `onShowBlocked(BlockReason.notReady)` instead of silently doing nothing, so the awaited
  Future always resolves.

## 0.3.0

* `AdFlowNativeAd.reload()`: force-fetch a new native ad even while the currently cached one is
  still within its expiry window (`load()` no-ops in that case). Bumps the underlying
  `adflow-core`/`adflow-admob` dependency to `v0.4.0`. Does not rebind any already-built
  `AdFlowNativeAdView` - recreate the widget (e.g. bump its `Key`) once `reload()` resolves
  successfully to pick up the new ad. If the reload fails, the previously cached ad is left in
  place. See README "Hiển thị từng loại ad" section for the recommended integration point
  (`RouteAware.didPopNext()`).

## 0.2.1

* Bump the underlying `adflow-core`/`adflow-admob` dependency to `v0.3.0`, which renames the
  default Logcat tag (was `"AdFlow"`, clashed with the app's own package name in logcat output;
  now `"AdFlowSDK"`). No Dart API change.

## 0.2.0

* GDPR/consent support (Google UMP), exposed on `AdFlowCore`: `getConsentStatus()`,
  `getPrivacyOptionsRequirement()`, `canRequestAds()`, `requestConsentIfNeeded()`,
  `showPrivacyOptionsForm()`. `requestConsentIfNeeded()` accepts an optional `debugGeography`/
  `testDeviceHashedIds` to test the EEA flow without rebuilding a native-only app.
* No app-side change required for existing `load()` calls - they already respect consent
  automatically (see README).

## 0.1.1

* Bump the underlying `adflow-core`/`adflow-admob` dependency to `v0.2.0`, which adds GDPR/consent
  support (Google UMP) on the native Android side. Not yet exposed through the Dart API - this
  release only picks up the new native dependency.

## 0.1.0

Initial release. Android only - iOS not implemented yet.

* Interstitial, App Open, Rewarded, Native, and Banner ad support, bridged to the underlying
  `adflow-core`/`adflow-admob` Kotlin libraries via Pigeon.
* `AdFlowCore.initialize()` for one-time setup (show-interval config, revenue logging).
* `AdFlowInterstitialAd`, `AdFlowAppOpenAd`, `AdFlowRewardedAd`, `AdFlowBannerAd`, `AdFlowNativeAd`
  facades, plus `AdFlowBannerAdView`/`AdFlowNativeAdView` widgets backed by `AndroidView`.
* `setEnabled()` per placement as a runtime on/off switch (e.g. for premium/remove-ads users).
* App Open auto-show on foreground via `enableAutoShowOnForeground()`.
