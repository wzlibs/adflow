# adflow_flutter

Flutter plugin bọc thư viện quảng cáo AdFlow (`:adflow-core` + `:adflow-admob`), hỗ trợ Interstitial, App Open, Rewarded, Native và Banner. **Hiện chỉ hỗ trợ Android** - iOS chưa implement (sẽ làm sau như 1 lib Swift riêng biệt).

## 1. Thêm vào project

### Cách 1 - qua pub.dev (khuyến nghị)

```bash
flutter pub add adflow_flutter
```

hoặc thêm thủ công vào `pubspec.yaml`:

```yaml
dependencies:
  adflow_flutter: ^0.2.0
```

### Cách 2 - qua `path:` (khi muốn test trực tiếp source chưa publish)

```yaml
dependencies:
  adflow_flutter:
    path: ../path/to/flutter/adflow_flutter
```

(đường dẫn tùy vị trí thực tế của thư mục chứa plugin trên máy bạn).

### Bước bắt buộc - khai báo repository JitPack

`adflow_flutter` phụ thuộc `adflow-core`/`adflow-admob`, được publish qua [JitPack](https://jitpack.io) (`com.github.wzlibs.adflow:core`/`admob`). Gradle **không tự động** cho app tiêu thụ thấy được repository này chỉ vì plugin đã khai báo nó - dependency của 1 configuration chỉ resolve qua repositories khai báo ở project sở hữu configuration đó (ở đây là chính app của bạn), không "mượn" repository của plugin dù có quan hệ dependency trực tiếp. Nếu bỏ qua bước này, build sẽ báo `Could not find com.github.wzlibs.adflow:core:v0.2.0`.

Thêm vào `android/build.gradle.kts` của app (theo đúng mẫu đang dùng ở `flutter/adflow_flutter/example/android/build.gradle.kts`):

```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

(nếu app của bạn dùng `dependencyResolutionManagement` trong `settings.gradle.kts` thay vì `allprojects{}` trong `build.gradle.kts`, thêm dòng `maven("https://jitpack.io")` tương tự vào đó).

Plugin yêu cầu `minSdk = 24` - app tiêu thụ cũng phải khai `minSdk` tối thiểu 24.

## 2. Khai báo AndroidManifest

Thêm App ID AdMob vào `android/app/src/main/AndroidManifest.xml` của app:

```xml
<application ...>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
</application>
```

## 3. Khởi tạo trong `main()`

```dart
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AdFlowCore.initialize();
  runApp(const MyApp());
}
```

`AdFlowCore.initialize()` nên gọi ngay trong `main()`, vì đó là thời điểm sớm nhất chắc chắn app sắp hiển thị UI. Nếu app của bạn nhúng Flutter kiểu add-to-app (Dart entrypoint không đảm bảo luôn dẫn tới foreground), hãy tự gate lời gọi này tới khi chắc chắn app đang hiển thị.

`initialize()` nhận thêm 2 tham số tùy chọn: `showIntervalConfig` (xem mục 7) và `useLogcatLogger` (mặc định `true`, log ra Logcat tag "AdFlowSDK").

## 4. Khai báo Placements

`adflow_flutter` không có sẵn 1 class "danh sách placement" - tự viết 1 class Dart phẳng (xem ví dụ đầy đủ ở `example/lib/ad_placements.dart`):

```dart
class AdPlacements {
  final splashInterstitial = AdFlowInterstitialAd(const PlacementConfig(
    placementId: 'splash_interstitial',
    adUnitIds: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
  ));

  final appOpen = AdFlowAppOpenAd(const PlacementConfig(
    placementId: 'app_open',
    adUnitIds: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
  ));

  final rewarded = AdFlowRewardedAd(const PlacementConfig(
    placementId: 'rewarded',
    adUnitIds: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
  ));

  final banner = AdFlowBannerAd(const PlacementConfig(
    placementId: 'home_banner',
    adUnitIds: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
  ));

  final native = AdFlowNativeAd(const PlacementConfig(
    placementId: 'home_native',
    adUnitIds: ['ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy'],
  ));
}
```

`PlacementConfig` có `placementId`, `adUnitIds` (waterfall), `enabled`, `preloadEnabled`, `retryPolicy`, `expiryMs`. Plugin không hỗ trợ rule gating dạng callback tùy biến (kiểu `AdRule`/`loadRule`/`showRule` phía native đồng bộ) vì việc đó cần round-trip qua Platform Channel mỗi lần load/show, không phù hợp cho 1 điều kiện được gọi thường xuyên. Để tắt/bật ads có điều kiện (vd user mua VIP), gọi `setEnabled(bool)` trên từng đối tượng ad:

```dart
await placements.splashInterstitial.setEnabled(!isVip);
```

`setEnabled(false)` chặn **cả `load()` lẫn hiển thị**, cho mọi loại ad - không chỉ `show()` của Interstitial/App Open/Rewarded, mà cả việc `load()` fetch ad mới (tránh tốn ad request ngầm vô ích khi đã tắt hẳn) và việc `AdFlowBannerAdView`/`AdFlowNativeAdView` render ad (kể cả khi build widget mà quên tự kiểm tra trạng thái VIP ở Dart). Trạng thái này chỉ tồn tại trong bộ nhớ (không tự lưu lại) - app tự lưu trạng thái VIP và gọi lại `setEnabled(false)` mỗi lần khởi động app, trước khi `loadAll()`.

## 5. GDPR/quyền riêng tư (Consent)

`adflow_flutter` bọc [Google User Messaging Platform (UMP)](https://developers.google.com/admob/android/privacy) - CMP chính thức của Google, tự phát hiện khu vực (EEA/UK) cần xin consent, app không cần tự viết logic phát hiện vùng.

Đây là 1 primitive độc lập - gọi `AdFlowCore.requestConsentIfNeeded()` ở bất kỳ đâu/lúc nào tuỳ ý, không có vị trí bắt buộc:

```dart
unawaited(AdFlowCore.requestConsentIfNeeded().then((_) => placements.loadAll()));
```

**Không cần tự viết điều kiện check consent trước khi gọi `load()`** - `load()` (ở mọi loại ad) tự động tôn trọng consent. Gọi `load()` trước khi consent resolve chỉ fail an toàn (giống bị `enabled: false`), không crash, không mất placement - gọi lại `load()` sau khi `requestConsentIfNeeded()` hoàn tất là đủ để load thật.

Chính sách AdMob/Google Play yêu cầu có lối vào để user xem lại/đổi consent đã chọn - chỉ hiện khi cần:

```dart
final requirement = await AdFlowCore.getPrivacyOptionsRequirement();
if (requirement == PPrivacyOptionsRequirement.required) {
  // hiện nút/menu "Privacy options", bấm vào gọi:
  await AdFlowCore.showPrivacyOptionsForm();
}
```

Để test flow EEA khi thiết bị test không ở EEA thật:

```dart
await AdFlowCore.requestConsentIfNeeded(
  debugGeography: PDebugGeography.eea,
  testDeviceHashedIds: ['TEST-DEVICE-HASHED-ID'],
);
```

`debugGeography` chỉ có hiệu lực trên thiết bị đã được đăng ký làm test device qua `testDeviceHashedIds` - trên thiết bị chưa đăng ký, nó bị bỏ qua âm thầm (không lỗi, chỉ đơn giản chạy như production thật). Xem [hướng dẫn testing chính thức của Google](https://developers.google.com/admob/android/privacy#testing) để lấy đúng hashed ID cho thiết bị test của bạn.

## 6. Hiển thị từng loại ad

**Interstitial:**

```dart
await placements.globalInterstitial.load();
if (await placements.globalInterstitial.isReady) {
  await placements.globalInterstitial.show(
    onAdDismissed: () { /* điều hướng tiếp */ },
    onAdFailedToShow: (error) { /* fallback */ },
    onShowBlocked: (reason) { /* bị chặn bởi show-interval hoặc setEnabled(false) */ },
  );
}
```

**App Open** - có thể show thủ công như Interstitial, hoặc bật tự động show khi app quay lại foreground:

```dart
await placements.appOpen.enableAutoShowOnForeground();
// ... và disableAutoShowOnForeground() nếu cần tắt lại
```

**Rewarded** - `show()` có thêm `onUserEarnedReward`:

```dart
await placements.rewarded.show(
  onUserEarnedReward: (reward) {
    // reward.type, reward.amount
  },
);
```

**Banner:**

```dart
Scaffold(
  body: ...,
  bottomNavigationBar: bannerReady
      ? SafeArea(child: AdFlowBannerAdView(ad: placements.banner))
      : null,
)
```

Nên đặt banner ở `bottomNavigationBar` của `Scaffold` thay vì trực tiếp trong 1 `Column` - đã xác nhận qua test thật: đặt trong `Column` khiến banner đè lên nội dung phía trên (do kích thước ad thật không khớp hoàn toàn với `SizedBox` mặc định), trong khi `bottomNavigationBar` tự chừa chỗ, tránh đè lên bất kỳ nội dung nào. `AdFlowBannerAdView` có `height` mặc định `50` (khớp `AdSize.BANNER` mà `AdMobBannerAdManager` dùng cố định) - khi thử nghiệm, dùng đúng test Ad Unit ID cho banner **fixed-size** (`ca-app-pub-3940256099942544/6300978111`), không dùng ID **Adaptive Banner** (`ca-app-pub-3940256099942544/9214589741`) vì kích thước thật sẽ không khớp `AdSize.BANNER`, gây lệch layout.

**Native:**

```dart
if (nativeReady) AdFlowNativeAdView(ad: placements.native),
```

`AdFlowNativeAdView` có `height` mặc định `250`, đủ cho renderer mặc định `DefaultMediumNativeAdRenderer` (headline + media + body + CTA) - đổi `height` phù hợp nếu dùng renderer khác.

**Custom native ad UI (`rendererId`):** UI native ad mặc định là `DefaultMediumNativeAdRenderer` (Kotlin, phía native). Vì `NativeAdRenderer` trả về 1 `View` Android thật (cần cho AdMob gắn click-tracking qua `NativeAdView.setNativeAd()`), 1 layout tùy biến hoàn toàn phải viết bằng Kotlin - không mô tả được bằng Dart widget tree. Các bước:

1. Viết 1 class Kotlin implement `NativeAdRenderer` (từ `adflow-core`), giống `DefaultMediumNativeAdRenderer`/`DefaultSmallNativeAdRenderer` (`adflow-admob`) làm mẫu - `createView(Context): View` trả về 1 `NativeAdView` chứa layout tùy ý, `bind(view, assets: NativeAdAssets)` gán dữ liệu (`headline`, `body`, `icon` - `Drawable?` đã decode sẵn từ `NativeAd.icon?.drawable`, `callToAction`, `starRating`, `advertiser`) vào các view con. Code này nằm trong app (module `android/app`), dùng trực tiếp `com.google.android.gms.ads.nativead.NativeAdView` - `adflow-admob` khai báo `play-services-ads` là `implementation` (không lộ transitive), nên phải tự thêm `implementation("com.google.android.gms:play-services-ads:<version>")` vào `android/app/build.gradle.kts` của app.
2. Đăng ký renderer đó với 1 `rendererId` (String) trong `MainActivity.kt`, sau `super.configureFlutterEngine(...)`:
   ```kotlin
   override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
       super.configureFlutterEngine(flutterEngine)
       AdflowFlutterPlugin.registerNativeAdRenderer(flutterEngine, "compactCard", MyCustomRenderer())
   }
   ```
3. Chọn renderer đó từ Dart qua `rendererId`:
   ```dart
   AdFlowNativeAdView(ad: placements.feedNative, rendererId: 'compactCard', height: 100),
   ```

Có thể đăng ký **nhiều renderer khác nhau** (gọi `registerNativeAdRenderer` nhiều lần với `rendererId` khác nhau) và dùng cho nhiều native ad placement khác nhau trong cùng 1 app - `rendererId` hoàn toàn độc lập với `placementId`, không giới hạn 1 renderer/app hay 1 renderer/placement. Nếu `rendererId` truyền từ Dart không khớp renderer nào đã đăng ký (vd quên gọi `registerNativeAdRenderer`, hoặc gọi trước khi `AdflowFlutterPlugin` kịp attach), tự động fallback về `DefaultMediumNativeAdRenderer` và log cảnh báo ra Logcat - không crash app.

`rendererId` không bắt buộc phải trỏ tới renderer tự viết - cũng đăng ký được renderer có sẵn trong `adflow-admob`, ví dụ `DefaultSmallNativeAdRenderer` (headline + icon + body, gọn hơn `DefaultMediumNativeAdRenderer` vì không có `MediaView`):
```kotlin
AdflowFlutterPlugin.registerNativeAdRenderer(flutterEngine, "small", DefaultSmallNativeAdRenderer())
```
Xem `example/android/app/.../MainActivity.kt` để có ví dụ đăng ký song song cả renderer có sẵn lẫn renderer tự viết.

Với cả banner lẫn native: **chỉ build widget sau khi `await ad.load()` đã thành công** - `load()` trả về 1 `Future` thật, tự nó đã đủ để biết chính xác thời điểm ad sẵn sàng, không cần polling `isReady()` thêm.

**Đổi sang native ad mới (`reload()`):** 1 native ad được cache và tái sử dụng vô thời hạn tới khi
hết hạn (mặc định 4h) - `load()` sẽ no-op nếu ad đang cache vẫn còn hạn. Nếu muốn ép đổi sang ad
mới dù ad cũ vẫn còn hạn (ví dụ: user rời màn hình đang hiển thị native ad rồi quay lại), gọi
`await ad.reload()`, rồi tự ép Flutter tạo lại `AdFlowNativeAdView` (đổi 1 `Key`, vì nó không tự
rebind sang ad mới):

```dart
final result = await placements.native.reload();
if (result.success) {
  setState(() => _nativeGeneration++); // dùng làm Key bên dưới
}
...
AdFlowNativeAdView(key: ValueKey(_nativeGeneration), ad: placements.native),
```

Điểm gọi khuyến nghị là `RouteAware.didPopNext()` (đăng ký 1 `RouteObserver` qua
`navigatorObservers` của `MaterialApp`) - tức lúc màn hình đang hiển thị native ad quay lại visible
sau khi route phía trên bị pop. Đây là quyết định của app, `reload()` không tự động phát hiện việc
này. Nếu `reload()` thất bại, ad cũ vẫn giữ nguyên trong cache, không bị mất.

## 7. Tùy chỉnh tần suất hiển thị

```dart
await AdFlowCore.initialize(
  showIntervalConfig: const ShowIntervalConfig(
    interstitialAfterInterstitialMs: 45000,
    appOpenAfterAppOpenMs: 60000,
    interstitialAfterAppOpenMs: 8000,
    appOpenAfterInterstitialMs: 8000,
  ),
);
```

## 8. Theo dõi doanh thu (tùy chọn)

```dart
AdFlowCore.addRevenueLogger((event) {
  // event.placementId, event.adType, event.adUnitId,
  // event.valueMicros, event.currencyCode, event.precision, event.adNetwork
});
```

## 9. Trước khi release

Toàn bộ App ID/Ad Unit ID trong ví dụ ở tài liệu này là **ID test chính thức của Google**. Phải thay bằng ID thật trước khi phát hành - dùng ID test khi release sẽ vi phạm chính sách AdMob.

## Giới hạn đã biết

- `AdRule` (loadRule/showRule) không bridge qua channel được - chỉ hỗ trợ on/off qua `setEnabled()`; logic gating phức tạp hơn (cooldown, theo giờ...) phải tự viết ở tầng Dart.
- Banner cố định `AdSize.BANNER` (320x50) - chưa hỗ trợ adaptive banner.
- Tag JitPack trong `android/build.gradle.kts` (`com.github.wzlibs.adflow:core:v0.2.0`/`admob:v0.2.0`) phải bump thủ công mỗi khi `adflow-core`/`adflow-admob` ra tag mới - quên bump sẽ khiến plugin build với version cũ một cách âm thầm.
- `show()` cần 1 `Activity` đang attach với Flutter engine - gọi quá sớm (chưa có Activity nào) sẽ báo `onShowBlocked(BlockReason.notReady)` thay vì hiển thị, không crash.
- `RetryPolicy` mặc định retry **không giới hạn** khi no-fill (backoff tăng dần, trần 60s/lần) - `load()` sẽ không tự bỏ cuộc, cứ thử mãi cho tới khi có fill hoặc app tự huỷ theo cách khác (vd tắt placement qua `setEnabled(false)`). Cần tính vào UX loading nếu app hiển thị spinner chờ `load()`.
- iOS: chưa hỗ trợ.

## Ví dụ đầy đủ

Xem `example/` - demo app Flutter tương đương vai trò `:app` bên native, đã verify chạy được trên thiết bị thật (mọi loại ad load/show được, banner/native render đúng, switch Premium chặn được ads). Chạy `flutter run` trong thư mục `example/` để xem trực tiếp.
