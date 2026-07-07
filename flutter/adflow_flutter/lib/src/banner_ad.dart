import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'config.dart';
import 'generated/adflow_api.g.dart';

const _bannerViewType = 'adflow/banner_ad_view';

/// Facade Dart cho 1 placement Banner. Không có `show()`/`getView()` như bản Kotlin thuần - dùng
/// [AdFlowBannerAdView] (PlatformView) để render sau khi [load] hoàn tất.
class AdFlowBannerAd {
  AdFlowBannerAd(PlacementConfig config) : placementId = config.placementId {
    _hostApi.create(config.toPigeon());
  }

  final String placementId;
  static final BannerAdHostApi _hostApi = BannerAdHostApi();

  Future<bool> get isReady => _hostApi.isReady(placementId);

  Future<PLoadResult> load() => _hostApi.load(placementId);

  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);
}

/// Render [ad] qua `PlatformView` Android thật (`BannerAdManager.getView()`). Chỉ nên build widget
/// này SAU KHI `await ad.load()` đã thành công - không tự poll `isReady()` như bản Compose gốc, vì
/// `load()` giờ là 1 Future thật (Pigeon `@async`).
class AdFlowBannerAdView extends StatelessWidget {
  const AdFlowBannerAdView({super.key, required this.ad, this.height = 50});

  final AdFlowBannerAd ad;
  final double height;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      child: AndroidView(
        viewType: _bannerViewType,
        creationParams: {'placementId': ad.placementId},
        creationParamsCodec: const StandardMessageCodec(),
      ),
    );
  }
}
