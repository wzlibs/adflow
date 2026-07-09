# AdFlow

AdFlow là thư viện quản lý quảng cáo cho app Android, hỗ trợ Interstitial, App Open, Rewarded, Native và Banner. Thư viện tách thành 2 phần: `adflow-core` (API độc lập với network quảng cáo) và `adflow-admob` (implementation dùng Google AdMob). Tài liệu này hướng dẫn cách nhúng AdFlow vào 1 app khác và dùng nó trong thực tế - không đi vào chi tiết bên trong lib hoạt động ra sao.

## 1. Thêm vào project

### Cách 1 - qua JitPack (khuyến nghị)

Trong `settings.gradle.kts` của app (khối `dependencyResolutionManagement`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Trong `build.gradle.kts` của app:

```kotlin
dependencies {
    implementation("com.github.wzlibs.adflow:core:v0.2.0")
    implementation("com.github.wzlibs.adflow:admob:v0.2.0")
}
```

(`v0.2.0` là tag release - xem tag mới nhất tại repo GitHub `wzlibs/adflow`. JitPack build theo yêu cầu ở lần đầu tiên có người dùng 1 tag mới, có thể mất khoảng 1-2 phút cho lần đầu).

### Cách 2 - include module trực tiếp

Copy 2 thư mục module `adflow-core`/`adflow-admob` vào project, hoặc gộp app vào cùng multi-module build này, rồi:

```kotlin
// settings.gradle.kts của app
include(":adflow-core")
include(":adflow-admob")
```

```kotlin
// build.gradle.kts của app
dependencies {
    implementation(project(":adflow-core"))
    implementation(project(":adflow-admob"))
}
```

---

Nếu app dùng các Compose helper của lib (`BannerAdView`, `NativeAdView`, xem mục 6) thì app phải tự có Jetpack Compose (Compose BOM + `androidx.compose.ui`) trong classpath của mình - `adflow-admob` không tự kéo Compose runtime cho app.

## 2. Khai báo AndroidManifest

App phải tự khai báo AdMob App ID trong `AndroidManifest.xml`:

```xml
<application ...>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
</application>
```

Giá trị `ca-app-pub-3940256099942544~3347511713` trong repo demo là **test App ID chính thức của Google** - chỉ dùng khi phát triển.

## 3. Khởi tạo trong `Application`

```kotlin
class MyApp : Application() {

    lateinit var placements: MyAdPlacements
        private set

    override fun onCreate() {
        super.onCreate()
        AdFlowCore.configure(logger = LogcatAdFlowLogger(tag = "AdFlowDebug"))
        placements = MyAdPlacements(this)

        // Chỉ init provider và load ads khi app thực sự vào foreground lần đầu - tránh lãng phí
        // ad request cho những lần process bị OS đánh thức chỉ để xử lý việc ở background (ví dụ
        // FCM push) mà không có Activity nào sắp hiển thị.
        AdFlowCore.runOnFirstForeground {
            placements.provider.initialize(this) {
                placements.splashInterstitial.load()
                placements.globalInterstitial.load()
                placements.appOpen.load()
                placements.rewarded.load()
                placements.banner.load()
                placements.native.load()
            }
        }

        // Tự động show App Open ad mỗi khi app quay lại foreground.
        AppOpenAdController(this, placements.appOpen).start()
    }
}
```

- `AdFlowCore.configure(...)` gọi 1 lần duy nhất, thiết lập logger và (tùy chọn) tần suất hiển thị chung (xem mục 7).
- `AdFlowCore.runOnFirstForeground { ... }` đảm bảo phần init SDK + load ads chỉ chạy khi có Activity thật sự vào foreground, không chạy nếu process chỉ được đánh thức để làm việc nền.
- `AppOpenAdController(application, appOpen).start()` lo việc tự show App Open ad mỗi khi app foreground; không cần tự gọi `show()` cho placement này ở nơi khác trừ khi muốn hiển thị thủ công.

## 4. GDPR/quyền riêng tư (Consent)

AdFlow bọc [Google User Messaging Platform (UMP)](https://developers.google.com/admob/android/privacy) - CMP chính thức của Google, tự phát hiện khu vực (EEA/UK) cần xin consent, app không cần tự viết logic phát hiện vùng.

`ConsentManager` là 1 primitive độc lập - **không có vị trí bắt buộc phải gọi**. Chọn 1 `Activity` nào tiện (Activity đầu tiên của app là lựa chọn tự nhiên nhất) và gọi:

```kotlin
placements.provider.createConsentManager(activity).requestConsentIfNeeded(activity) { error ->
    // resolve xong (có thể đã hiện form, hoặc không cần vì ngoài EEA/UK) - gọi lại load() ở đây
    // cho các placement muốn dùng ngay, phòng trường hợp lần load() trước đó (nếu có) đã bị chặn
    // vì consent chưa resolve.
    placements.splashInterstitial.load()
    placements.globalInterstitial.load()
    // ...
}
```

**Không cần tự viết điều kiện check consent trước khi gọi `load()`** - `load()` tự động tôn trọng consent (mặc định cho phép nếu app không tích hợp `ConsentManager`, để không phá vỡ hành vi hiện có). Nếu gọi `load()` trước khi consent resolve, nó chỉ fail an toàn (giống bị `enabled = false`), không crash, không mất placement - gọi lại `load()` sau khi `requestConsentIfNeeded` hoàn tất là đủ để load thật.

Chính sách AdMob/Google Play yêu cầu có lối vào để user xem lại/đổi consent đã chọn - chỉ hiện khi cần:

```kotlin
val consentManager = placements.provider.createConsentManager(context)
if (consentManager.getPrivacyOptionsRequirement() == PrivacyOptionsRequirement.REQUIRED) {
    // hiện nút/menu "Privacy options", bấm vào gọi:
    consentManager.showPrivacyOptionsForm(activity) { error -> }
}
```

Để test flow EEA khi máy/thiết bị test không ở EEA thật, dùng `AdMobConsentManager` (implementation cụ thể của `adflow-admob`) với debug settings:

```kotlin
AdMobConsentManager(
    context,
    debugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
    testDeviceHashedIds = listOf("TEST-DEVICE-HASHED-ID"),
)
```

`debugGeography` chỉ có hiệu lực trên thiết bị đã được đăng ký làm test device qua `testDeviceHashedIds` - trên thiết bị chưa đăng ký, nó bị bỏ qua âm thầm (không lỗi, chỉ đơn giản chạy như production thật). Xem [hướng dẫn testing chính thức của Google](https://developers.google.com/admob/android/privacy#testing) để lấy đúng hashed ID cho thiết bị test của bạn.

## 5. Khai báo Placements

AdFlow không có sẵn 1 class "danh sách placement" - mỗi app tự viết class riêng của mình theo pattern sau (đặt tên tùy ý, ví dụ `MyAdPlacements`):

```kotlin
class MyAdPlacements(context: Context) {

    // Dòng duy nhất gắn app với 1 network implementation cụ thể;
    // đổi AdMobProvider sang implementation AdNetworkProvider khác để chuyển network.
    val provider: AdNetworkProvider = AdMobProvider(context)

    private val notPremium = AdRule { !PremiumState.isPremium }

    val splashInterstitial: InterstitialAdManager = provider.createInterstitial(
        PlacementConfig(
            placementId = "splash_interstitial",
            adUnitIds = listOf("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )

    val appOpen: AppOpenAdManager = provider.createAppOpen(
        PlacementConfig(
            placementId = "app_open",
            adUnitIds = listOf("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )

    val rewarded: RewardedAdManager = provider.createRewarded(
        PlacementConfig(
            placementId = "rewarded",
            adUnitIds = listOf("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy"),
        ),
    )

    val banner: BannerAdManager = provider.createBanner(
        PlacementConfig(
            placementId = "home_banner",
            adUnitIds = listOf("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )

    val native: NativeAdManager = provider.createNative(
        PlacementConfig(
            placementId = "home_native",
            adUnitIds = listOf("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )
}
```

Các field hay dùng của `PlacementConfig`:

- `placementId` - định danh duy nhất cho placement này, dùng trong log và trong `AdRule`.
- `adUnitIds` - danh sách ad unit ID theo thứ tự waterfall (thử ID đầu, hết fill thì rơi xuống ID kế tiếp).
- `loadRule` / `showRule` - kiểu `AdRule { isAllowed(placementId): Boolean }`, dùng để tắt load/show có điều kiện (ví dụ user đã mua gói premium thì trả về `false`). Với Interstitial/App Open/Rewarded, `showRule` từ chối thì `show()` gọi `onShowBlocked(BlockReason.RULE_REJECTED)` qua callback. Với Native/Banner - vốn không có `show(callback)` riêng, chỉ có `createView()`/`getView()` trả thẳng `View` - `showRule` từ chối làm `createView()`/`getView()` **throw `IllegalStateException`** (cùng cách "not ready" đã throw sẵn) thay vì trả `View`; tự kiểm tra điều kiện tương đương (hoặc gọi trong `try/catch`) trước khi build `View` nếu dùng `showRule` cho Native/Banner.
- `retryPolicy` và `expiryMs` có giá trị mặc định hợp lý (retry backoff khi load lỗi, ad hết hạn sau 4 giờ) - chỉ cần chỉnh khi có yêu cầu đặc biệt.

## 6. Hiển thị từng loại ad

**Interstitial / App Open** (show thủ công - App Open còn có thể tự show qua `AppOpenAdController` ở mục 3):

```kotlin
if (placements.globalInterstitial.isReady()) {
    placements.globalInterstitial.show(activity, object : ShowCallback {
        override fun onAdDismissed() { /* điều hướng tiếp sau khi đóng ad */ }
        override fun onAdFailedToShow(error: AdFlowError) { /* fallback */ }
        override fun onShowBlocked(reason: BlockReason) { /* bị chặn bởi show-interval/showRule */ }
    })
}
```

Nếu không cần xử lý callback, dùng `ShowCallback.NONE`:

```kotlin
placements.appOpen.show(activity, ShowCallback.NONE)
```

**Rewarded:**

```kotlin
placements.rewarded.show(activity, object : RewardedAdCallback {
    override fun onUserEarnedReward(reward: RewardItem) {
        // cộng thưởng cho user: reward.amount, reward.type
    }
})
```

**Banner:**

```kotlin
// View truyền thống
val bannerView: View = placements.banner.getView(context)

// Compose
if (placements.banner.isReady()) {
    BannerAdView(manager = placements.banner)
}
```

**Native:**

```kotlin
// View truyền thống, cần 1 NativeAdRenderer (có thể viết renderer tùy biến)
val nativeView: View = placements.native.createView(context, MyCustomRenderer())

// Compose, dùng renderer mặc định có sẵn
if (placements.native.isReady()) {
    NativeAdView(manager = placements.native, renderer = DefaultMediumNativeAdRenderer())
}
```

Có 2 renderer dựng sẵn trong `adflow-admob`: `DefaultMediumNativeAdRenderer` (headline + media + body + CTA, dọc) và `DefaultSmallNativeAdRenderer` (headline + icon + body, gọn hơn, không có `MediaView`, dùng khi không đủ chỗ cho ảnh media lớn - vd item trong list). Viết `NativeAdRenderer` riêng nếu cần layout khác - `bind(view, assets: NativeAdAssets)` nhận `headline`, `body`, `icon` (`Drawable?` đã decode sẵn từ `NativeAd.icon?.drawable`, gán thẳng qua `ImageView.setImageDrawable()`), `callToAction`, `starRating`, `advertiser`.

`BannerAdView`/`NativeAdView` tự kiểm tra `isReady()` bên trong, nhưng Compose sẽ không tự recompose khi ad load xong ở background - nên bọc thêm 1 state được cập nhật qua polling `isReady()` (ví dụ vòng lặp `delay(500)` trong `LaunchedEffect`) nếu muốn ad tự xuất hiện ngay khi sẵn sàng thay vì chỉ ở lần recompose kế tiếp.

**Đổi sang native ad mới (`reload()`):** khác với Interstitial/Rewarded (tự "tiêu thụ" khi `show()`
nên tự nhiên đã reload), 1 native ad được cache và tái sử dụng vô thời hạn cho tới khi hết hạn
(`PlacementConfig.expiryMs`, mặc định 4h) - `load()` sẽ no-op nếu ad đang cache vẫn còn hạn. Nếu
muốn ép đổi sang ad mới dù ad cũ vẫn còn hạn (ví dụ: user rời màn hình đang hiển thị native ad rồi
quay lại, và muốn thấy ad khác), gọi `reload()`:

```kotlin
placements.native.reload { result ->
    if (result is AdLoadResult.Success) {
        // ad mới đã load xong và thay cho ad cũ trong cache - ép tạo lại View để đọc ad mới
    }
}
```

`reload()` **không** tự rebind `View`/`NativeAdView` đang hiển thị - `createView()` chỉ đọc
`cachedAd` tại đúng lúc được gọi. App phải tự ép tạo lại View sau khi callback báo thành công (ví
dụ đổi giá trị 1 Compose `key()` bọc quanh `NativeAdView`, xem `app/.../HomeScreen.kt`). Nếu
`reload()` thất bại, ad cũ vẫn giữ nguyên trong cache - không bị mất/hết hạn oan.

Điểm gọi khuyến nghị là lifecycle callback ứng với lúc user "quay lại" màn hình đang hiển thị native
ad (`onResume` của Activity/Fragment, hoặc tương đương) - đây là quyết định của app, thư viện không
tự động phát hiện việc này (mỗi app có kiến trúc navigation khác nhau: Activity, Fragment, Jetpack
Navigation, Compose Navigation...).

## 7. Tùy chỉnh tần suất hiển thị

Mặc định AdFlow áp 1 khoảng nghỉ tối thiểu giữa các lần hiển thị Interstitial/App Open để tránh làm phiền user. Tùy chỉnh qua `ShowIntervalConfig` khi gọi `configure()`:

```kotlin
AdFlowCore.configure(
    showIntervalConfig = ShowIntervalConfig(
        interstitialAfterInterstitialMs = 45_000, // giữa 2 lần Interstitial
        appOpenAfterAppOpenMs = 60_000,            // giữa 2 lần App Open
        interstitialAfterAppOpenMs = 8_000,        // Interstitial ngay sau khi vừa show App Open
        appOpenAfterInterstitialMs = 8_000,        // App Open ngay sau khi vừa show Interstitial
    ),
)
```

## 8. Theo dõi doanh thu (tùy chọn)

Đăng ký 1 `RevenueLogger` để forward sự kiện doanh thu sang hệ thống đo lường của app (Adjust, AppsFlyer, Firebase...):

```kotlin
AdFlowCore.addRevenueLogger(RevenueLogger { event: AdRevenueEvent ->
    // event.placementId, event.adType, event.adUnitId,
    // event.valueMicros, event.currencyCode, event.precision, event.adNetwork
})
```

## 9. Logging (tùy chọn)

Mặc định AdFlowSDK log qua Logcat (`LogcatAdFlowLogger`). Truyền 1 `AdFlowLogger` tùy biến vào `configure()` nếu muốn gửi log đi nơi khác:

```kotlin
AdFlowCore.configure(logger = object : AdFlowLogger {
    override fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String?) {
        // ghi log theo cách của app
    }
})
```

## 10. Trước khi release

Toàn bộ App ID và Ad Unit ID dùng trong ví dụ ở tài liệu này (`ca-app-pub-3940256099942544/...`) là **ID test chính thức của Google**. Trước khi phát hành app, phải thay bằng App ID và Ad Unit ID thật được tạo trong tài khoản AdMob của app - dùng ID test khi release sẽ vi phạm chính sách AdMob.
