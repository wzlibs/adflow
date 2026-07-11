import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'ad_flow_dispatcher.dart';
import 'ad_state.dart';
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
}

class AdFlowBanner extends StatelessWidget {
  const AdFlowBanner(
    this.placementId, {
    super.key,
    this.height = 50,
    this.loading,
    this.failed,
  });

  final String placementId;
  final double height;
  final WidgetBuilder? loading;
  final Widget Function(BuildContext context, AdFlowError error)? failed;

  @override
  Widget build(BuildContext context) {
    final ad = AdFlowBannerAd(placementId);
    return ValueListenableBuilder<AdState>(
      valueListenable: ad.state,
      builder: (context, state, _) {
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
