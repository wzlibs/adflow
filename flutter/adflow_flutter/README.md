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
  adflow_flutter: ^0.1.0
```

### Cách 2 - qua `path:` (khi muốn test trực tiếp source chưa publish)

```yaml
dependencies:
  adflow_flutter:
    path: ../path/to/flutter/adflow_flutter
```

(đường dẫn tùy vị trí thực tế của thư mục chứa plugin trên máy bạn).

### Bước bắt buộc - khai báo repository JitPack

`adflow_flutter` phụ thuộc `adflow-core`/`adflow-admob`, được publish qua [JitPack](https://jitpack.io) (`com.github.wzlibs.adflow:core`/`admob`). Gradle **không tự động** cho app tiêu thụ thấy được repository này chỉ vì plugin đã khai báo nó - dependency của 1 configuration chỉ resolve qua repositories khai báo ở project sở hữu configuration đó (ở đây là chính app của bạn), không "mượn" repository của plugin dù có quan hệ dependency trực tiếp. Nếu bỏ qua bước này, build sẽ báo `Could not find com.github.wzlibs.adflow:core:v0.1.0`.

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

`initialize()` nhận thêm 2 tham số tùy chọn: `showIntervalConfig` (xem mục 7) và `useLogcatLogger` (mặc định `true`, log ra Logcat tag "AdFlow").

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

`PlacementConfig` có `placementId`, `adUnitIds` (waterfall), `enabled`, `preloadEnabled`, `retryPolicy`, `expiryMs`. Plugin không hỗ trợ rule gating dạng callback tùy biến (kiểu `AdRule` đồng bộ) vì việc đó cần round-trip qua Platform Channel mỗi lần load/show, không phù hợp cho 1 điều kiện được gọi thường xuyên. Để tắt/bật ads có điều kiện (vd user premium), gọi `setEnabled(bool)` trên từng đối tượng ad:

```dart
await placements.splashInterstitial.setEnabled(!isPremium);
```

## 5. Hiển thị từng loại ad

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

`AdFlowNativeAdView` có `height` mặc định `250`, đủ cho renderer mặc định `DefaultMediumNativeAdRenderer` (headline + media + body + CTA) - renderer hiện cố định, chưa expose custom renderer qua Dart.

Với cả banner lẫn native: **chỉ build widget sau khi `await ad.load()` đã thành công** - `load()` trả về 1 `Future` thật, tự nó đã đủ để biết chính xác thời điểm ad sẵn sàng, không cần polling `isReady()` thêm.

## 6. Tùy chỉnh tần suất hiển thị

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

## 7. Theo dõi doanh thu (tùy chọn)

```dart
AdFlowCore.addRevenueLogger((event) {
  // event.placementId, event.adType, event.adUnitId,
  // event.valueMicros, event.currencyCode, event.precision, event.adNetwork
});
```

## 8. Trước khi release

Toàn bộ App ID/Ad Unit ID trong ví dụ ở tài liệu này là **ID test chính thức của Google**. Phải thay bằng ID thật trước khi phát hành - dùng ID test khi release sẽ vi phạm chính sách AdMob.

## Giới hạn đã biết

- `AdRule` (loadRule/showRule) không bridge qua channel được - chỉ hỗ trợ on/off qua `setEnabled()`; logic gating phức tạp hơn (cooldown, theo giờ...) phải tự viết ở tầng Dart.
- Banner cố định `AdSize.BANNER` (320x50) - chưa hỗ trợ adaptive banner.
- Native renderer cố định `DefaultMediumNativeAdRenderer` - chưa expose custom renderer qua Dart.
- Tag JitPack trong `android/build.gradle.kts` (`com.github.wzlibs.adflow:core:v0.1.0`/`admob:v0.1.0`) phải bump thủ công mỗi khi `adflow-core`/`adflow-admob` ra tag mới - quên bump sẽ khiến plugin build với version cũ một cách âm thầm.
- `show()` cần 1 `Activity` đang attach với Flutter engine - gọi quá sớm (chưa có Activity nào) sẽ bị bỏ qua âm thầm, không crash.
- `RetryPolicy` mặc định có thể khiến `load()` treo tới ~135s ở trường hợp xấu nhất (backoff 5+10+20+40+60s) - cần tính vào UX loading của app.
- iOS: chưa hỗ trợ.

## Ví dụ đầy đủ

Xem `example/` - demo app Flutter tương đương vai trò `:app` bên native, đã verify chạy được trên thiết bị thật (mọi loại ad load/show được, banner/native render đúng, switch Premium chặn được ads). Chạy `flutter run` trong thư mục `example/` để xem trực tiếp.
