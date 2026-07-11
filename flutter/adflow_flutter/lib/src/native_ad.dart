import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'ad_flow_dispatcher.dart';
import 'ad_state.dart';
import 'generated/adflow_api.g.dart';
import 'types.dart';

const _nativeViewType = 'adflow/native_ad_view';

class AdFlowNativeAd {
  AdFlowNativeAd(this.placementId);

  final String placementId;
  static final AdHostApi _hostApi = AdHostApi();

  ValueListenable<AdState> get state =>
      AdFlowDispatcher.instance.stateOf(placementId);
  Future<void> load() => _hostApi.load(placementId);
  Future<void> reload() => _hostApi.reload(placementId);
}

class AdFlowNative extends StatelessWidget {
  const AdFlowNative(
    this.placementId, {
    super.key,
    this.height = 250,
    this.rendererId,
    this.loading,
    this.failed,
  });

  final String placementId;
  final double height;
  final String? rendererId;
  final WidgetBuilder? loading;
  final Widget Function(BuildContext context, AdFlowError error)? failed;

  @override
  Widget build(BuildContext context) {
    final ad = AdFlowNativeAd(placementId);
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
          width: double.infinity,
          child: AndroidView(
            viewType: _nativeViewType,
            creationParams: {
              'placementId': placementId,
              if (rendererId != null) 'rendererId': rendererId,
            },
            creationParamsCodec: const StandardMessageCodec(),
          ),
        );
      },
    );
  }
}
