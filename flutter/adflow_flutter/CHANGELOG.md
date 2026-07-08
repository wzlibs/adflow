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
