import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'config.dart';
import 'generated/adflow_api.g.dart';

const _nativeViewType = 'adflow/native_ad_view';

/// Facade Dart cho 1 placement Native. Giống [AdFlowBannerAd] - dùng [AdFlowNativeAdView] để
/// render sau khi [load] hoàn tất. Renderer hiện cố định là `DefaultMediumNativeAdRenderer` phía
/// Kotlin cho v1 - custom renderer chưa expose qua Dart (giới hạn đã biết, xem README).
class AdFlowNativeAd {
  AdFlowNativeAd(PlacementConfig config) : placementId = config.placementId {
    _hostApi.create(config.toPigeon());
  }

  final String placementId;
  static final NativeAdHostApi _hostApi = NativeAdHostApi();

  Future<bool> get isReady => _hostApi.isReady(placementId);

  Future<PLoadResult> load() => _hostApi.load(placementId);

  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);
}

/// `AndroidView` không tự "wrap content" như 1 View Android bình thường - Flutter cần biết trước
/// kích thước để cấp phát layout, nếu không platform view sẽ co về cao 0 (vô hình) khi đặt trong
/// 1 Column. `height` mặc định 250 đủ chỗ cho template `DefaultMediumNativeAdRenderer` (headline +
/// media + body + CTA) - chỉnh lại nếu dùng renderer khác.
class AdFlowNativeAdView extends StatelessWidget {
  const AdFlowNativeAdView({super.key, required this.ad, this.height = 250});

  final AdFlowNativeAd ad;
  final double height;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      width: double.infinity,
      child: AndroidView(
        viewType: _nativeViewType,
        creationParams: {'placementId': ad.placementId},
        creationParamsCodec: const StandardMessageCodec(),
      ),
    );
  }
}
