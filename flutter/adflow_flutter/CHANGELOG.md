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
