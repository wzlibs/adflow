# adflow_flutter

Android-only Flutter bridge for AdFlow v2. Placements are declared once, ad lifecycle is exposed
as reactive state, and banner/native platform views manage loading and rebinding themselves.

## Setup

Add the package, set Android `minSdk` to 24, and add JitPack to the app's repositories:

```kotlin
repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}
```

Add the AdMob app ID to `android/app/src/main/AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
```

## Initialize

Declare every placement in one place. Ad unit lists are tried as a waterfall.

```dart
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AdFlow.initialize(
    placements: const [
      InterstitialPlacement(
        'splash_interstitial',
        adUnits: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
        preload: false,
      ),
      InterstitialPlacement(
        'global_interstitial',
        adUnits: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
      ),
      AppOpenPlacement(
        'app_open',
        adUnits: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
        autoShowOnForeground: true,
      ),
      RewardedPlacement(
        'rewarded',
        adUnits: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
      ),
      BannerPlacement(
        'home_banner',
        adUnits: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
      ),
      NativePlacement(
        'home_native',
        adUnits: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
        rendererId: 'medium',
      ),
    ],
  );
  runApp(const MyApp());
}
```

Use `showInterval`, `useLogcatLogger`, `consentDebugGeography`, and
`consentDebugTestDeviceHashedIds` on `initialize` when those settings are needed. Consent debug
settings must only be used for test devices.

## Full-screen ads

Every handle exposes a stable `ValueListenable<AdState>`. `awaitReady` starts an idempotent load
and returns on loaded, failed, or timeout, so splash screens do not need polling.

```dart
final ad = AdFlow.interstitial('splash_interstitial');
final state = await ad.awaitReady(const Duration(seconds: 8));
if (state is AdLoaded) {
  await ad.show(
    onDismissed: continueNavigation,
    onFailedToShow: (error) => continueNavigation(),
    onBlocked: (reason) => continueNavigation(),
  );
}

await AdFlow.rewarded('rewarded').show(
  onUserEarnedReward: (reward) => grantReward(reward.amount),
);
```

States are `AdIdle`, `AdLoading`, `AdLoaded`, `AdFailed`, and `AdShowing`. Block reasons distinguish
loading, no-fill, consent, rule rejection, interval throttling, and another full-screen ad.

## Banner and native widgets

The widgets react to placement state. No readiness polling, generation keys, or manual platform
view recreation is required.

```dart
AdFlowBanner(
  'home_banner',
  loading: (_) => const SizedBox(height: 50, child: LinearProgressIndicator()),
  failed: (_, error) => const SizedBox.shrink(),
)

AdFlowNative(
  'home_native',
  height: 250,
  rendererId: 'medium',
  loading: (_) => const SizedBox(height: 250, child: LinearProgressIndicator()),
  failed: (_, error) => const SizedBox.shrink(),
)

await AdFlow.native('home_native').reload();
```

The Android `AdFlowBannerView` and `AdFlowNativeAdView` own attach, load, collapse, and rebind
behavior. A successful native reload is reflected without changing the Flutter widget key.

## Global controls and consent

```dart
await AdFlow.setAdsEnabled(!isPremium);

final error = await AdFlow.requestConsentIfNeeded();
final requirement = await AdFlow.getPrivacyOptionsRequirement();
if (requirement == PrivacyOptionsRequirement.required) {
  await AdFlow.showPrivacyOptionsForm();
}

AdFlow.addRevenueLogger((event) {
  // Forward event to analytics or attribution.
});
```

`setAdsEnabled(false)` gates loading and showing for every placement. Re-enabling ads triggers a
new demand-driven load for registered placements.

## Custom native renderer

Implement native v2's `NativeAdRenderer` in the Android app:

```kotlin
class CompactRenderer : NativeAdRenderer {
    override fun onCreateView(context: Context, parent: ViewGroup): View =
        NativeAdView(context) // Add and register asset views here.

    override fun onBind(view: View, assets: NativeAdAssets) {
        // Bind headline, body, CTA, media, and other assets.
    }
}
```

Register it after `super.configureFlutterEngine` and use the same ID in a `NativePlacement` or
`AdFlowNative` widget:

```kotlin
AdflowFlutterPlugin.registerNativeAdRenderer(flutterEngine, "compact", CompactRenderer())
```

Unknown renderer IDs fall back to `DefaultMediumNativeAdRenderer` with a Logcat warning.

See `example/` for a complete integration using Google test ad unit IDs.
