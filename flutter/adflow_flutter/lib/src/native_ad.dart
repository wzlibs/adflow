import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'config.dart';
import 'flutter_api_dispatcher.dart';
import 'generated/adflow_api.g.dart';

const _nativeViewType = 'adflow/native_ad_view';

/// Facade Dart cho 1 placement Native. Giống [AdFlowBannerAd] - dùng [AdFlowNativeAdView] để
/// render sau khi [load] hoàn tất. Renderer mặc định là `DefaultMediumNativeAdRenderer` phía
/// Kotlin - app có thể chọn 1 renderer tùy biến khác qua [AdFlowNativeAdView.rendererId] (xem
/// README).
class AdFlowNativeAd {
  AdFlowNativeAd(PlacementConfig config) : placementId = config.placementId {
    _hostApi.create(config.toPigeon());
  }

  final String placementId;
  static final NativeAdHostApi _hostApi = NativeAdHostApi();

  Future<bool> get isReady => _hostApi.isReady(placementId);

  Future<PLoadResult> load() => _hostApi.load(placementId);

  /// Ép fetch 1 ad mới thật sự dù ad đang cache vẫn còn hạn - dùng khi muốn đổi sang ad mới (vd
  /// user quay lại 1 màn hình đang hiển thị native ad, qua [RouteAware.didPopNext]). Không tự
  /// rebind [AdFlowNativeAdView] đang hiển thị - sau khi [reload] trả về thành công, tự ép
  /// Flutter tạo lại widget đó (vd đổi `Key`) để nó đọc ad mới.
  Future<PLoadResult> reload() => _hostApi.reload(placementId);

  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);
}

/// `AndroidView` không tự "wrap content" như 1 View Android bình thường - Flutter cần biết trước
/// kích thước để cấp phát layout, nếu không platform view sẽ co về cao 0 (vô hình) khi đặt trong
/// 1 Column. `height` mặc định 250 đủ chỗ cho template `DefaultMediumNativeAdRenderer` (headline +
/// media + body + CTA) - chỉnh lại nếu dùng renderer khác.
///
/// Luôn an toàn để build ngay, không cần `await ad.load()` hay check `isReady` trước - phía
/// Kotlin (`NativeAdManager.createView()`) không throw, chỉ render 1 View rỗng và báo lý do qua
/// [onShowBlocked] khi bị chặn (chưa `load()` xong, hoặc `showRule` từ chối). Nhưng platform view
/// Android chỉ được tạo đúng 1 lần lúc build - nếu bị chặn ngay từ đầu, nó sẽ kẹt ở trạng thái
/// rỗng mãi trừ khi được build lại với 1 [Key] mới. Pattern khuyến nghị: dùng [onShowBlocked] để
/// đặt cờ "đang bị chặn" (ẩn widget này đi bằng cách bọc `if (!blocked)`), rồi poll định kỳ để
/// bump 1 giá trị dùng làm [Key] khi còn đang bị chặn, ép build lại và tự thử lần nữa cho tới khi
/// thành công - xem `example/lib/home_screen.dart` để có ví dụ đầy đủ.
class AdFlowNativeAdView extends StatefulWidget {
  const AdFlowNativeAdView({
    super.key,
    required this.ad,
    this.height = 250,
    this.rendererId,
    this.onShowBlocked,
  });

  final AdFlowNativeAd ad;
  final double height;

  /// Chọn 1 `NativeAdRenderer` Kotlin app tự viết, đã đăng ký phía native qua
  /// `AdflowFlutterPlugin.registerNativeAdRenderer(flutterEngine, rendererId, renderer)` (thường
  /// gọi trong `MainActivity.configureFlutterEngine()`). Độc lập với [ad]/placementId - có thể
  /// đăng ký nhiều renderer khác nhau và dùng cho nhiều placement khác nhau trong cùng 1 app.
  /// `null` (mặc định) dùng renderer có sẵn `DefaultMediumNativeAdRenderer`. Nếu id không khớp
  /// renderer nào đã đăng ký, tự fallback về renderer mặc định (xem log Logcat cảnh báo), không
  /// crash app.
  final String? rendererId;

  /// Gọi lại khi native ad không hiển thị được (chưa `load()` xong -
  /// [PBlockReason.notReady], hoặc `showRule` đang từ chối - [PBlockReason.ruleRejected]). Tuỳ
  /// chọn - bỏ qua nếu không cần biết lý do.
  final void Function(PBlockReason reason)? onShowBlocked;

  @override
  State<AdFlowNativeAdView> createState() => _AdFlowNativeAdViewState();
}

class _AdFlowNativeAdViewState extends State<AdFlowNativeAdView> {
  @override
  void initState() {
    super.initState();
    FlutterApiDispatcher.instance.registerShowEventHandler(widget.ad.placementId, (
      kind,
      error,
      blockReason,
      reward,
    ) {
      if (kind == PShowEventKind.showBlocked && blockReason != null) {
        widget.onShowBlocked?.call(blockReason);
      }
    });
  }

  @override
  void dispose() {
    FlutterApiDispatcher.instance.unregisterShowEventHandler(widget.ad.placementId);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: widget.height,
      width: double.infinity,
      child: AndroidView(
        viewType: _nativeViewType,
        creationParams: {
          'placementId': widget.ad.placementId,
          if (widget.rendererId != null) 'rendererId': widget.rendererId,
        },
        creationParamsCodec: const StandardMessageCodec(),
      ),
    );
  }
}
