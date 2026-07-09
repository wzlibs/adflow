import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'config.dart';
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
class AdFlowNativeAdView extends StatelessWidget {
  const AdFlowNativeAdView({super.key, required this.ad, this.height = 250, this.rendererId});

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
