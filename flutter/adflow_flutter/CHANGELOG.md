## 0.1.0

Initial release. Android only - iOS not implemented yet.

* Interstitial, App Open, Rewarded, Native, and Banner ad support, bridged to the underlying
  `adflow-core`/`adflow-admob` Kotlin libraries via Pigeon.
* `AdFlowCore.initialize()` for one-time setup (show-interval config, revenue logging).
* `AdFlowInterstitialAd`, `AdFlowAppOpenAd`, `AdFlowRewardedAd`, `AdFlowBannerAd`, `AdFlowNativeAd`
  facades, plus `AdFlowBannerAdView`/`AdFlowNativeAdView` widgets backed by `AndroidView`.
* `setEnabled()` per placement as a runtime on/off switch (e.g. for premium/remove-ads users).
* App Open auto-show on foreground via `enableAutoShowOnForeground()`.
