import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'ad_flow_dispatcher.dart';
import 'ad_state.dart';
import 'ad_state_effects.dart';
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

  /// Bật/tắt runtime cho đúng placement này - độc lập, không ảnh hưởng placement khác. Khi tắt,
  /// [load]/[reload] và render đều bị chặn ngay, không chạm network thật. Bật lại tự kích [load]
  /// lại.
  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);
}

class AdFlowNative extends StatefulWidget {
  const AdFlowNative(
    this.placementId, {
    super.key,
    this.height = 250,
    this.rendererId,
    this.loading,
    this.failed,
    this.onLoading,
    this.onLoaded,
    this.onError,
  });

  final String placementId;
  final double height;
  final String? rendererId;
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
  State<AdFlowNative> createState() => _AdFlowNativeState();
}

class _AdFlowNativeState extends State<AdFlowNative> {
  late AdFlowNativeAd _ad;

  @override
  void initState() {
    super.initState();
    _ad = AdFlowNativeAd(widget.placementId);
    // Cùng lý do với AdFlowBanner - xem KDoc/comment ở đó.
    unawaited(_ad.load());
  }

  @override
  void didUpdateWidget(AdFlowNative oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.placementId == widget.placementId) return;
    _ad = AdFlowNativeAd(widget.placementId);
    unawaited(_ad.load());
  }

  @override
  Widget build(BuildContext context) {
    return AdStateEffects(
      state: _ad.state,
      onLoading: widget.onLoading,
      onLoaded: widget.onLoaded,
      onError: widget.onError,
      builder: (context, state) {
        if (state case AdFailed(:final error)) {
          return widget.failed?.call(context, error) ?? const SizedBox.shrink();
        }
        if (state is! AdLoaded) {
          return widget.loading?.call(context) ?? const SizedBox.shrink();
        }
        return SizedBox(
          height: widget.height,
          width: double.infinity,
          child: AndroidView(
            viewType: _nativeViewType,
            creationParams: {
              'placementId': widget.placementId,
              if (widget.rendererId != null) 'rendererId': widget.rendererId,
            },
            creationParamsCodec: const StandardMessageCodec(),
          ),
        );
      },
    );
  }
}
