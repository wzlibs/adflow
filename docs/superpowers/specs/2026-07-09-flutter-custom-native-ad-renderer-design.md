# Custom native ad renderer cho Flutter (adflow_flutter)

## Bối cảnh

Phía native Android thuần, `NativeAdManager.createView(context, renderer: NativeAdRenderer)` đã nhận
1 `NativeAdRenderer` tùy ý từ app (`adflow-core`/`adflow-admob`, đã có từ trước, không đổi trong
feature này) - app tự viết layout riêng bằng cách implement interface này. Nhưng phía Flutter,
`NativePlatformViewFactory.create()` (module `flutter/adflow_flutter`) đang hard-code
`DefaultMediumNativeAdRenderer()`, không có cách nào cho app Flutter chọn 1 renderer khác - đây là
giới hạn đã được ghi chú sẵn trong code (`NativePlatformViewFactory.kt`, README gốc và README
Flutter).

Nhu cầu: mỗi app dùng `adflow_flutter` muốn 1 UI native ad hoàn toàn riêng (khác cấu trúc, không
chỉ đổi màu/font) - tức phải viết layout đó bằng Kotlin thật (`NativeAdRenderer` trả về 1
`android.view.View`/`NativeAdView` thật), vì Dart widget tree không mô tả được 1 `View` Android
gắn AdMob click-tracking (`NativeAdView.setNativeAd()`). Đây là giới hạn kỹ thuật cố hữu, không
phải thiếu sót implementation.

Vì `adflow-core`/`adflow-admob` đã hỗ trợ sẵn, toàn bộ thay đổi của feature này nằm gọn trong
module `flutter/adflow_flutter` - không cần bump version 2 module native đó.

## Kiến trúc

Pattern: app đăng ký 1 `NativeAdRenderer` (Kotlin) theo `rendererId` (String) với
`AdflowFlutterPlugin`, gắn theo từng `FlutterEngine` cụ thể - giống hệt cách
`google_mobile_ads.GoogleMobileAdsPlugin.registerNativeAdFactory()` làm, để an toàn nếu app có
nhiều `FlutterEngine` cùng lúc (add-to-app). Dart chỉ cần truyền đúng `rendererId` đó khi tạo
`AdFlowNativeAdView` để chọn renderer nào dùng cho platform view đó; việc chọn renderer là kênh
đồng bộ qua `creationParams` của `AndroidView` (cơ chế Flutter có sẵn, đang dùng để truyền
`placementId`) - không cần đổi Pigeon vì đây không phải lời gọi tới ad-loading pipeline.

Renderer không đăng ký (rendererId `null`, hoặc không tìm thấy trong registry) → fallback
`DefaultMediumNativeAdRenderer()` (hành vi mặc định hiện tại, không breaking change cho app đã
dùng `AdFlowNativeAdView` mà chưa biết tới `rendererId`).

## 1. Đăng ký renderer theo `FlutterEngine`

File: `flutter/adflow_flutter/android/src/main/kotlin/com/adflow/adflow_flutter/AdflowFlutterPlugin.kt`

```kotlin
class AdflowFlutterPlugin : FlutterPlugin, ActivityAware {
    private val nativeAdRenderers = mutableMapOf<String, NativeAdRenderer>()

    companion object {
        /** Đăng ký 1 [NativeAdRenderer] Kotlin app tự viết, chọn được từ Dart qua tham số
         * `rendererId` của [AdFlowNativeAdView]. Gọi trong `MainActivity.configureFlutterEngine()`,
         * sau `super.configureFlutterEngine(flutterEngine)` (để [AdflowFlutterPlugin] đã kịp
         * `onAttachedToEngine`). */
        fun registerNativeAdRenderer(
            flutterEngine: FlutterEngine,
            rendererId: String,
            renderer: NativeAdRenderer,
        ) {
            val plugin = flutterEngine.plugins.get(AdflowFlutterPlugin::class.java) as? AdflowFlutterPlugin
                ?: throw IllegalStateException(
                    "AdflowFlutterPlugin chưa được đăng ký với FlutterEngine này - gọi " +
                        "registerNativeAdRenderer() sau super.configureFlutterEngine(flutterEngine)"
                )
            plugin.nativeAdRenderers[rendererId] = renderer
        }

        /** Gỡ đăng ký - tùy chọn, dọn dẹp khi 1 renderer không còn cần dùng nữa (vd hot-reload
         * dev, hoặc engine sắp bị huỷ thủ công). */
        fun unregisterNativeAdRenderer(flutterEngine: FlutterEngine, rendererId: String) {
            val plugin = flutterEngine.plugins.get(AdflowFlutterPlugin::class.java) as? AdflowFlutterPlugin
            plugin?.nativeAdRenderers?.remove(rendererId)
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // ... như hiện tại ...
        binding.platformViewRegistry.registerViewFactory(
            NATIVE_VIEW_TYPE,
            NativePlatformViewFactory(registry, nativeAdRenderers), // truyền thẳng reference map,
            // để các lần registerNativeAdRenderer() gọi SAU thời điểm này vẫn được factory thấy.
        )
    }
}
```

`nativeAdRenderers` là property instance (không phải `companion object` map dùng chung mọi
engine) - mỗi `FlutterEngine` có 1 `AdflowFlutterPlugin` instance riêng khi `onAttachedToEngine`
chạy, nên mỗi engine có registry renderer độc lập, đúng yêu cầu an toàn multi-engine.

## 2. Chọn renderer lúc tạo platform view

File: `flutter/adflow_flutter/android/src/main/kotlin/com/adflow/adflow_flutter/platformview/NativePlatformViewFactory.kt`

```kotlin
class NativePlatformViewFactory(
    private val registry: PlacementRegistry,
    private val renderers: Map<String, NativeAdRenderer>,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val map = args as? Map<*, *>
        val placementId = map?.get("placementId") as? String
        val rendererId = map?.get("rendererId") as? String
        val manager = placementId?.let { registry.natives[it] }
        return NativePlatformView(context, manager, resolveRenderer(rendererId, renderers))
    }
}

/** Tách riêng khỏi [NativePlatformViewFactory.create] để test được bằng mock, không cần Robolectric
 * (module này chưa có Robolectric - [NativePlatformViewFactory]/[BannerPlatformViewFactory] hiện
 * không có test nào, đây cũng là code đầu tiên của nhóm platformview có test). */
internal fun resolveRenderer(rendererId: String?, renderers: Map<String, NativeAdRenderer>): NativeAdRenderer {
    if (rendererId == null) return DefaultMediumNativeAdRenderer()
    return renderers[rendererId] ?: run {
        Log.w(
            TAG,
            "Không tìm thấy NativeAdRenderer đã đăng ký cho rendererId='$rendererId' - dùng " +
                "renderer mặc định. Kiểm tra đã gọi AdflowFlutterPlugin.registerNativeAdRenderer() " +
                "trong MainActivity.configureFlutterEngine() trước khi tạo AdFlowNativeAdView với " +
                "rendererId này chưa.",
        )
        DefaultMediumNativeAdRenderer()
    }
}

private class NativePlatformView(
    context: Context,
    manager: NativeAdManager?,
    renderer: NativeAdRenderer,
) : PlatformView {
    private val view: View = manager?.createView(context, renderer) ?: FrameLayout(context)
    override fun getView(): View = view
    override fun dispose() {}
}
```

Không tìm thấy renderer không bao giờ làm crash app - luôn fallback về renderer mặc định (khớp
triết lý "an toàn/fallback" đã áp dụng cho `reload()` thất bại: ad cũ vẫn giữ nguyên thay vì màn
hình trắng).

## 3. Dart (`AdFlowNativeAdView`)

File: `flutter/adflow_flutter/lib/src/native_ad.dart`

```dart
class AdFlowNativeAdView extends StatelessWidget {
  const AdFlowNativeAdView({super.key, required this.ad, this.height = 250, this.rendererId});

  final AdFlowNativeAd ad;
  final double height;

  /// Chọn 1 `NativeAdRenderer` Kotlin đã đăng ký qua
  /// `AdflowFlutterPlugin.registerNativeAdRenderer()` phía native Android. `null` (mặc định) dùng
  /// renderer có sẵn `DefaultMediumNativeAdRenderer`. Nếu id không khớp renderer nào đã đăng ký,
  /// tự fallback về renderer mặc định (xem log Logcat cảnh báo), không crash.
  final String? rendererId;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      width: double.infinity,
      child: AndroidView(
        viewType: _nativeViewType,
        creationParams: {
          'placementId': ad.placementId,
          if (rendererId != null) 'rendererId': rendererId,
        },
        creationParamsCodec: const StandardMessageCodec(),
      ),
    );
  }
}
```

`height` vẫn do app tự set phù hợp với layout renderer đang chọn (như hiện tại) - renderer tùy
biến có thể cần `height` khác 250 (giá trị mặc định chỉ đúng cho `DefaultMediumNativeAdRenderer`).

## 4. Test

**`flutter/adflow_flutter/android/src/test/kotlin/com/adflow/adflow_flutter/platformview/NativePlatformViewFactoryTest.kt`**
(file mới) - test `resolveRenderer()` bằng `mock<NativeAdRenderer>()` (mockito-kotlin, đã có sẵn
là test dependency của module):
1. `rendererId == null trả về DefaultMediumNativeAdRenderer` - assert kiểu trả về.
2. `rendererId khớp 1 renderer đã đăng ký trả về đúng instance đó` - map có 1 mock, assert
   `resolveRenderer("id", map) === mock`.
3. `rendererId không khớp renderer nào trong map trả về DefaultMediumNativeAdRenderer` (không
   crash, không throw).

Không viết test cho `registerNativeAdRenderer`/`unregisterNativeAdRenderer` (companion functions)
- cần 1 `FlutterEngine` thật để gọi `flutterEngine.plugins.get(...)`, module này chưa có
Robolectric; khớp với thực tế `NativePlatformViewFactory`/`BannerPlatformViewFactory` hiện tại
cũng chưa có test nào cho phần glue Android Framework. Verify thủ công qua demo app (mục 5).

## 5. Demo app (verify thủ công đầu-cuối)

**`flutter/adflow_flutter/example/android/app/src/main/kotlin/com/adflow/adflow_flutter_example/CompactCardNativeAdRenderer.kt`**
(file mới) - 1 `NativeAdRenderer` layout **ngang** (icon trái, headline+body xếp dọc bên phải, CTA
`Button` cuối) - cố tình khác hẳn cấu trúc `DefaultMediumNativeAdRenderer` (dọc: headline → media →
body → cta) để thấy rõ ràng đây là 1 renderer khác, không phải style lại renderer mặc định.

**`MainActivity.kt`** cùng thư mục - đăng ký renderer:
```kotlin
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        AdflowFlutterPlugin.registerNativeAdRenderer(
            flutterEngine,
            "compactCard",
            CompactCardNativeAdRenderer(),
        )
    }
}
```

**`flutter/adflow_flutter/example/lib/home_screen.dart`** - thêm 1 `AdFlowNativeAdView` thứ 2 cạnh
view mặc định hiện có, dùng `rendererId: 'compactCard'` và `height` phù hợp layout ngang (vd `100`),
để app chạy demo thấy song song 2 layout khác nhau cho cùng 1 native ad placement.

## 6. Docs

`flutter/adflow_flutter/README.md`, mục Native - gỡ dòng "renderer cố định, chưa expose custom
renderer qua Dart" hiện có, thay bằng:
- Cách viết 1 `NativeAdRenderer` Kotlin (implement `createView(Context): View` trả về
  `NativeAdView`, `bind(view, NativeAdAssets)`).
- Cách đăng ký qua `AdflowFlutterPlugin.registerNativeAdRenderer(flutterEngine, rendererId,
  renderer)` trong `MainActivity.configureFlutterEngine()`.
- Cách chọn từ Dart qua `AdFlowNativeAdView(ad: ..., rendererId: 'id', height: ...)`.
- Lưu ý fallback an toàn khi `rendererId` không khớp renderer nào đã đăng ký.

`README.md` (root) không cần đổi - phần Native renderer tùy biến phía native Android thuần đã có
tài liệu đầy đủ từ trước, không đổi trong feature này.

## 7. Version/release

Không cần bump version `adflow-core`/`adflow-admob` (không đổi 2 module đó). Chỉ cần bump
`flutter/adflow_flutter/pubspec.yaml` (hiện `0.3.2`) và thêm entry `CHANGELOG.md` mô tả
`AdFlowNativeAdView.rendererId` + `AdflowFlutterPlugin.registerNativeAdRenderer()` mới, theo
`flutter/adflow_flutter/RELEASING.md`. Việc thực sự publish/tag vẫn là bước riêng, cần xác nhận
rõ ràng.

## Kiểm tra (verification)

1. `cd flutter/adflow_flutter/android && ./gradlew test` (hoặc chạy qua `example/android`) - test
   mới của `resolveRenderer()` pass, không phá test hiện có.
2. `cd flutter/adflow_flutter && flutter analyze` - xác nhận `rendererId` mới compile sạch phía
   Dart.
3. `cd flutter/adflow_flutter/example/android && ./gradlew :app:assembleDebug` - xác nhận
   `CompactCardNativeAdRenderer`/`MainActivity` mới compile được, `AdflowFlutterPlugin` companion
   functions dùng đúng.
4. Thủ công: chạy `flutter/adflow_flutter/example/`, xác nhận thấy 2 native ad view cạnh nhau với
   2 layout rõ ràng khác nhau (mặc định dọc, `compactCard` ngang) cho cùng 1 placement.

### File quan trọng
- `flutter/adflow_flutter/android/src/main/kotlin/com/adflow/adflow_flutter/AdflowFlutterPlugin.kt`
- `flutter/adflow_flutter/android/src/main/kotlin/com/adflow/adflow_flutter/platformview/NativePlatformViewFactory.kt`
- `flutter/adflow_flutter/lib/src/native_ad.dart`
- `flutter/adflow_flutter/android/src/test/kotlin/com/adflow/adflow_flutter/platformview/NativePlatformViewFactoryTest.kt`
- `flutter/adflow_flutter/example/android/app/src/main/kotlin/com/adflow/adflow_flutter_example/MainActivity.kt`
- `flutter/adflow_flutter/example/android/app/src/main/kotlin/com/adflow/adflow_flutter_example/CompactCardNativeAdRenderer.kt`
- `flutter/adflow_flutter/example/lib/home_screen.dart`
- `flutter/adflow_flutter/README.md`
- `flutter/adflow_flutter/CHANGELOG.md`
- `flutter/adflow_flutter/pubspec.yaml`
