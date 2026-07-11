# AdFlow

AdFlow là thư viện quản lý quảng cáo cho app Android, hỗ trợ Interstitial, App Open, Rewarded,
Native và Banner. Thư viện tách thành 3 phần: `adflow-core` (API độc lập với network quảng cáo,
state-first - mỗi placement expose `StateFlow<AdState>` + listener), `adflow-admob`
(implementation dùng Google AdMob), và `adflow-compose` (composable slot cho Banner/Native, tùy
chọn - chỉ cần nếu app dùng Jetpack Compose). Tài liệu này hướng dẫn cách nhúng AdFlow vào 1 app
khác và dùng nó trong thực tế - không đi vào chi tiết bên trong lib hoạt động ra sao (xem
`docs/superpowers/specs/` cho kiến trúc chi tiết).

> **Bản 1.0 viết lại toàn bộ API** so với 0.x - không tương thích ngược. Xem mục 11 nếu bạn đang
> nâng cấp từ 0.x.

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
    implementation("com.github.wzlibs.adflow:core:v1.0.0-alpha01")
    implementation("com.github.wzlibs.adflow:admob:v1.0.0-alpha01")
    // Chỉ cần nếu dùng AdFlowBanner()/AdFlowNative() composable (mục 6) - app XML-only bỏ qua dòng này.
    implementation("com.github.wzlibs.adflow:compose:v1.0.0-alpha01")
}
```

(`v1.0.0-alpha01` là tag release - xem tag mới nhất tại repo GitHub `wzlibs/adflow`. JitPack build
theo yêu cầu ở lần đầu tiên có người dùng 1 tag mới, có thể mất khoảng 1-2 phút cho lần đầu).

### Cách 2 - include module trực tiếp

Copy 3 thư mục module `adflow-core`/`adflow-admob`/`adflow-compose` vào project, hoặc gộp app vào
cùng multi-module build này, rồi:

```kotlin
// settings.gradle.kts của app
include(":adflow-core")
include(":adflow-admob")
include(":adflow-compose")
```

```kotlin
// build.gradle.kts của app
dependencies {
    implementation(project(":adflow-core"))
    implementation(project(":adflow-admob"))
    implementation(project(":adflow-compose")) // tùy chọn, chỉ cần nếu dùng Compose
}
```

## 2. Khai báo AndroidManifest

App phải tự khai báo AdMob App ID trong `AndroidManifest.xml`:

```xml
<application ...>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
</application>
```

Giá trị `ca-app-pub-3940256099942544~3347511713` trong repo demo là **test App ID chính thức của
Google** - chỉ dùng khi phát triển.

## 3. Khởi tạo - `AdFlow.initialize { }`

Toàn bộ placement được khai báo tập trung trong **1 khối DSL duy nhất**, gọi 1 lần ở
`Application.onCreate()` - không còn pattern "app tự viết class placements + wire provider" của
0.x:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdFlow.initialize(this) {
            // Dòng duy nhất gắn app với 1 network implementation cụ thể; đổi AdMobNetwork() sang
            // implementation AdNetwork khác để chuyển network.
            network = AdMobNetwork()
            logger = LogcatAdFlowLogger(tag = "AdFlowDebug")

            interstitial("splash_interstitial") {
                adUnits("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy")
                loadWhen { !PremiumState.isPremium }
                showWhen { !PremiumState.isPremium }
            }
            interstitial("global_interstitial") {
                adUnits("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy")
            }
            appOpen("app_open") {
                adUnits("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy")
                // true: tự động show mỗi khi app quay lại foreground - không cần tự viết
                // AppOpenAdController như 0.x, không bao giờ đè lên full-screen ad khác.
                autoShowOnForeground = true
            }
            rewarded("rewarded") {
                adUnits("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy")
            }
            banner("home_banner") {
                adUnits("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy")
            }
            native("home_native") {
                adUnits("ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy")
                renderer = DefaultMediumNativeAdRenderer() // bắt buộc set 1 renderer, xem mục 6
            }
        }
    }
}
```

Sau khi khởi tạo, lấy controller ở **bất kỳ đâu khác** bằng `placementId` đã khai báo:

```kotlin
AdFlow.interstitial("global_interstitial")   // InterstitialAd
AdFlow.appOpen("app_open")                    // AppOpenAd
AdFlow.rewarded("rewarded")                   // RewardedAd
AdFlow.banner("home_banner")                  // BannerAdController
AdFlow.native("home_native")                  // NativeAdController
```

Gọi sai `placementId` (chưa khai báo, hoặc khai báo với loại ad khác) sẽ throw ngay với thông báo
lỗi rõ ràng - fail fast thay vì âm thầm trả về null.

`AdFlow.initialize()` tự động:
- Trì hoãn `network.initialize()` + `load()` mọi placement có `preload = true` (mặc định) tới lúc
  app **thực sự** vào foreground lần đầu - tránh lãng phí ad request khi process chỉ bị OS đánh
  thức để xử lý việc nền (FCM push...) mà không có Activity nào sắp hiển thị.
- Gọi lần 2 trở đi là no-op (chỉ log cảnh báo) - an toàn nếu `onCreate()` vô tình chạy lại.

Các field hay dùng trong DSL của mỗi placement:

- `adUnits(...)` - bắt buộc, danh sách ad unit ID theo thứ tự waterfall (thử ID đầu, hết fill thì
  rơi xuống ID kế tiếp).
- `loadWhen { }` / `showWhen { }` - điều kiện app tự định nghĩa để chặn load/show (ví dụ user đã
  mua premium thì trả về `false`). Muốn tắt hẳn 1 placement (không bao giờ load), dùng
  `loadWhen { false }` - không có field `enabled` riêng, `loadRule` đã đủ diễn đạt trường hợp này.
- `preload`, `expiry` (không có ở Banner - xem mục 7), `retryPolicy` - có giá trị mặc định hợp lý,
  chỉ cần chỉnh khi có yêu cầu đặc biệt.

## 4. Trạng thái (state) - thay cho callback 1 lần của 0.x

Mọi placement expose `state: StateFlow<AdState>`:

```kotlin
sealed interface AdState {
    data object Idle : AdState
    data object Loading : AdState
    data class Loaded(val loadedAtMs: Long) : AdState
    data class Failed(val error: AdFlowError, val willRetry: Boolean, val nextRetryDelayMs: Long?) : AdState
    data object Showing : AdState   // chỉ full-screen (Interstitial/App Open/Rewarded)
}
```

Dùng trực tiếp để tự vẽ UI (shimmer lúc `Loading`, ẩn slot lúc `Failed`...) mà không cần
`isReady()`/poll như 0.x:

```kotlin
val state by AdFlow.banner("home_banner").state.collectAsStateWithLifecycle()
```

Hoặc dùng listener kiểu AdMob (cho code không dùng coroutines - XML/View truyền thống):

```kotlin
AdFlow.interstitial("global_interstitial").addListener(object : AdListener {
    override fun onAdLoaded() { /* ... */ }
    override fun onAdFailedToLoad(error: AdFlowError, willRetry: Boolean) { /* ... */ }
    override fun onAdBlocked(reason: BlockReason) { /* ... */ }
})
```

Listener mới đăng ký được **replay ngay trạng thái hiện tại** - không bao giờ lỡ mất `onAdLoaded()`
vì đăng ký muộn.

**Retry mặc định hữu hạn 3 chu kỳ** (backoff 5s/10s/20s) rồi kết thúc `Failed(willRetry = false)`
- không còn retry vô hạn nền như 0.x. Placement không kẹt chết: 1 lượt load mới (đếm lại từ 0) tự
  mở khi có nhu cầu thật (view attach lại, `show()` tự chữa lành, gọi `load()`/`reload()` thủ công,
  app quay lại foreground với placement `preload = true`). Muốn hành vi vô hạn thì tự set
  `retryPolicy = RetryPolicy(maxRetries = Int.MAX_VALUE)`.

`BlockReason` tách rõ **đang load** và **hết retry, không có gì để chờ**:

```kotlin
enum class BlockReason {
    STILL_LOADING, NO_AD_AVAILABLE,
    CONSENT_REQUIRED, RULE_REJECTED, INTERVAL_NOT_ELAPSED, ANOTHER_AD_SHOWING,
}
```

## 5. Hiển thị Interstitial / App Open / Rewarded

```kotlin
AdFlow.interstitial("global_interstitial").show(activity, object : FullScreenCallback {
    override fun onAdDismissed() { /* điều hướng tiếp sau khi đóng ad */ }
    override fun onAdFailedToShow(error: AdFlowError) { /* fallback */ }
    override fun onAdBlocked(reason: BlockReason) { /* STILL_LOADING/NO_AD_AVAILABLE/INTERVAL_NOT_ELAPSED/... */ }
})
```

Không cần xử lý callback: `show(activity, FullScreenCallback.EMPTY)`.

**Rewarded** nhận `RewardedAdCallback` (thêm `onUserEarnedReward`), hoặc dùng overload tiện:

```kotlin
AdFlow.rewarded("rewarded").show(activity) { reward -> /* reward.amount, reward.type */ }
```

**Splash pattern** - đợi ad ready trong tối đa N giây rồi show, hết giờ thì đi tiếp không show:

```kotlin
LaunchedEffect(Unit) {
    val splash = AdFlow.interstitial("splash_interstitial")
    if (splash.awaitReady(8.seconds)) {
        splash.show(activity, callback)
    } else {
        navigateHome()
    }
}
```

## 6. Banner / Native - view tự quản lý, không còn polling

Khác biệt lớn nhất so với 0.x: `AdFlowBannerView`/`AdFlowNativeAdView` **tự quan sát state và tự
cập nhật nội dung** - không cần `isReady()`, không cần `LaunchedEffect`+`delay`+`key()` bump để
"ép" Compose tạo lại view khi ad load xong muộn.

### XML / View truyền thống

```xml
<com.adflow.core.banner.AdFlowBannerView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adflowPlacementId="home_banner" />

<com.adflow.core.nativead.AdFlowNativeAdView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adflowPlacementId="home_native" />
```

Hoặc gán bằng code: `view.placementId = "home_banner"`. View tự `load()`, tự attach ad thật khi
`Loaded`, tự collapse (`visibility = GONE`, không chiếm layout - tắt qua `view.autoCollapse =
false` nếu muốn tự quản lý visibility) khi chưa có/bị chặn, tự nở lại khi ad đến muộn. Gán
`view.adListener` nếu cần biết chính xác lý do bị chặn (`onAdBlocked`) hay lỗi load
(`onAdFailedToLoad`).

Native cần `NativeAdRenderer` - lấy từ DSL (`native(id) { renderer = ... }`) hoặc gán riêng cho
view (`view.renderer = ...`, override renderer mặc định của placement). Viết renderer riêng nếu 2
renderer dựng sẵn không đủ:

```kotlin
class MyRenderer : NativeAdRenderer {
    override fun onCreateView(context: Context, parent: ViewGroup): View { /* dựng layout riêng */ }
    override fun onBind(view: View, assets: NativeAdAssets) { /* gán headline/body/icon/cta... */ }
}
```

Có 2 renderer dựng sẵn trong `adflow-admob`: `DefaultMediumNativeAdRenderer` (headline + media +
body + CTA, dọc) và `DefaultSmallNativeAdRenderer` (headline + icon + body, gọn hơn, dùng khi
không đủ chỗ cho media lớn - vd item trong list).

### Jetpack Compose (`adflow-compose`)

```kotlin
AdFlowBanner(
    placementId = "home_banner",
    loading = { ShimmerBox(height = 60.dp) },   // hiện lúc Idle/Loading
    // failed = { error -> ... }                // hiện lúc Failed - mặc định rỗng, slot tự biến mất
)

AdFlowNative(
    placementId = "home_native",
    renderer = null, // null = dùng renderer mặc định khai báo trong DSL
    loading = { ShimmerBox(height = 120.dp) },
    failed = { Text("No ad") },
)
```

**Đổi sang native ad mới (`reload()`):** khác Interstitial/Rewarded (tự "tiêu thụ" khi `show()`),
1 native ad được cache và tái sử dụng cho tới khi hết hạn (`expiry`, mặc định 4h). Muốn ép đổi ad
mới dù ad cũ còn hạn (vd user rời rồi quay lại màn hình đang hiển thị native ad):

```kotlin
AdFlow.native("home_native").reload()
```

View/composable **tự rebind** khi ad mới load xong - không cần app ép tạo lại view như 0.x
(không còn cần bump `key()`).

## 7. Tùy chỉnh retry, expiry, tần suất hiển thị

```kotlin
interstitial("global_interstitial") {
    adUnits("...")
    retryPolicy = RetryPolicy(initialDelayMs = 5_000, multiplier = 2.0, maxDelayMs = 60_000, maxRetries = 3)
    expiry = 4.hours   // không có ở banner() - banner không bao giờ hết hạn
}
```

Khoảng nghỉ tối thiểu giữa các lần hiển thị Interstitial/App Open (mặc định 30s cùng loại, 6s khác
loại):

```kotlin
AdFlow.initialize(this) {
    network = AdMobNetwork()
    showIntervals {
        interstitialAfterInterstitial = 45.seconds
        appOpenAfterAppOpen = 60.seconds
        interstitialAfterAppOpen = 8.seconds
        appOpenAfterInterstitial = 8.seconds
    }
    // ...
}
```

## 8. GDPR/quyền riêng tư (Consent)

```kotlin
AdFlow.consent.requestIfNeeded(activity) { error ->
    // resolve xong (có thể đã hiện form, hoặc không cần vì ngoài EEA/UK)
}
```

**Không cần tự viết điều kiện check consent trước khi gọi `load()`** - `load()` tự động tôn trọng
consent (mặc định cho phép nếu app chưa gọi `requestIfNeeded`, để không phá vỡ hành vi mặc định).

Chính sách AdMob/Google Play yêu cầu có lối vào để user xem lại/đổi consent đã chọn:

```kotlin
if (AdFlow.consent.privacyOptionsRequirement == PrivacyOptionsRequirement.REQUIRED) {
    // hiện nút "Privacy options", bấm vào gọi:
    AdFlow.consent.showPrivacyOptionsForm(activity) { error -> }
}
```

Test flow EEA khi máy test không ở EEA thật:

```kotlin
AdFlow.initialize(this) {
    network = AdMobNetwork()
    consentDebug {
        geography = ConsentDebugGeography.EEA
        testDeviceHashedIds = listOf("TEST-DEVICE-HASHED-ID")
    }
    // ...
}
```

## 9. Theo dõi doanh thu (tùy chọn)

```kotlin
AdFlow.initialize(this) {
    network = AdMobNetwork()
    revenueLogger { event: AdRevenueEvent ->
        // event.placementId, event.adType, event.adUnitId,
        // event.valueMicros, event.currencyCode, event.precision, event.adNetwork
    }
    // ...
}
// hoặc đăng ký/gỡ sau khi đã initialize:
AdFlow.addRevenueLogger(myLogger)
AdFlow.removeRevenueLogger(myLogger)
```

## 10. Logging (tùy chọn)

Mặc định log qua Logcat (`LogcatAdFlowLogger`). Truyền `logger = ...` trong `AdFlow.initialize {}`
nếu muốn gửi log đi nơi khác - xem chữ ký `AdFlowLogger.log(placementId, adType, event, detail)`.

## 11. Nâng cấp từ 0.x

Bản 1.0 viết lại toàn bộ API - không có shim tương thích ngược, đổi mã theo bảng:

| 0.x | 1.0 |
|---|---|
| Tự viết class `Placements` + `AdNetworkProvider` | `AdFlow.initialize { }` DSL |
| `InterstitialAdManager`/`AppOpenAdManager`/... | `AdFlow.interstitial(id)`/`AdFlow.appOpen(id)`/... |
| `isReady()` + `load(cb)` callback 1 lần | `state: StateFlow<AdState>` + `AdListener` |
| `BlockReason.NOT_READY` | Tách `STILL_LOADING`/`NO_AD_AVAILABLE` |
| `getView()`/`createView()` trả View tĩnh, cần poll+`key()` | `AdFlowBannerView`/`AdFlowNativeAdView` tự quản lý |
| `AppOpenAdController(app, manager).start()` | `appOpen(id) { autoShowOnForeground = true }` |
| `AdFlowCore.configure(...)` | `AdFlow.initialize { }` |
| Retry mặc định vô hạn | Retry mặc định 3 chu kỳ + demand-driven (mục 4) |

## 12. Trước khi release

Toàn bộ App ID và Ad Unit ID dùng trong ví dụ ở tài liệu này (`ca-app-pub-3940256099942544/...`) là
**ID test chính thức của Google**. Trước khi phát hành app, phải thay bằng App ID và Ad Unit ID
thật được tạo trong tài khoản AdMob của app - dùng ID test khi release sẽ vi phạm chính sách AdMob.
