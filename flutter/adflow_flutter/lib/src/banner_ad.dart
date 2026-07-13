import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'ad_flow_dispatcher.dart';
import 'ad_state.dart';
import 'ad_state_effects.dart';
import 'generated/adflow_api.g.dart';
import 'types.dart';

const _bannerViewType = 'adflow/banner_ad_view';

class AdFlowBannerAd {
  AdFlowBannerAd(this.placementId);

  final String placementId;
  static final AdHostApi _hostApi = AdHostApi();

  ValueListenable<AdState> get state =>
      AdFlowDispatcher.instance.stateOf(placementId);
  Future<void> load() => _hostApi.load(placementId);

  /// Bật/tắt runtime cho đúng placement này - độc lập, không ảnh hưởng placement khác. Khi tắt,
  /// [load] và render đều bị chặn ngay, không chạm network thật. Bật lại tự kích [load] lại.
  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);
}

class AdFlowBanner extends StatelessWidget {
  const AdFlowBanner(
    this.placementId, {
    super.key,
    this.height = 50,
    this.loading,
    this.failed,
    this.onLoading,
    this.onLoaded,
    this.onError,
  });

  final String placementId;
  final double height;
  final WidgetBuilder? loading;
  final Widget Function(BuildContext context, AdFlowError error)? failed;

  /// Side-effect (vd `setState()` cho 1 biến khác) khi ad chưa sẵn sàng (gồm cả `AdIdle` lẫn
  /// `AdLoading`) - khác [loading] (trả về Widget, chạy trong lúc build, không an toàn để làm
  /// side-effect). Luôn chạy sau khi frame hiện tại build xong.
  final VoidCallback? onLoading;

  /// Side-effect khi ad đã load xong (`AdLoaded`). Khác [failed]/[loading] - xem [onLoading].
  final VoidCallback? onLoaded;

  /// Side-effect khi 1 lượt load thất bại (`AdFailed`). Khác [failed] (trả về Widget) - xem
  /// [onLoading].
  final void Function(AdFlowError error)? onError;

  @override
  Widget build(BuildContext context) {
    final ad = AdFlowBannerAd(placementId);
    return AdStateEffects(
      state: ad.state,
      onLoading: onLoading,
      onLoaded: onLoaded,
      onError: onError,
      builder: (context, state) {
        if (state case AdFailed(:final error)) {
          return failed?.call(context, error) ?? const SizedBox.shrink();
        }
        if (state is! AdLoaded) {
          return loading?.call(context) ?? const SizedBox.shrink();
        }
        return SizedBox(
          height: height,
          child: AndroidView(
            viewType: _bannerViewType,
            creationParams: {'placementId': placementId},
            creationParamsCodec: const StandardMessageCodec(),
          ),
        );
      },
    );
  }
}
