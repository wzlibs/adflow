import 'package:flutter/foundation.dart';

import 'ad_flow_dispatcher.dart';
import 'ad_state.dart';
import 'generated/adflow_api.g.dart';
import 'show_event_support.dart';
import 'types.dart';

class AdFlowAppOpenAd {
  AdFlowAppOpenAd(this.placementId);

  final String placementId;
  static final AdHostApi _hostApi = AdHostApi();

  ValueListenable<AdState> get state =>
      AdFlowDispatcher.instance.stateOf(placementId);
  Future<void> load() => _hostApi.load(placementId);

  /// Bật/tắt runtime cho đúng placement này - độc lập, không ảnh hưởng placement khác. Khi tắt,
  /// [load]/[show] bị chặn ngay, không chạm network thật. Bật lại tự kích [load] lại.
  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);

  /// true nếu gọi [show] ngay bây giờ sẽ thực sự tiến hành hiển thị - đã tính cả showRule,
  /// khoảng nghỉ tối thiểu giữa 2 lần hiển thị, và slot full-screen có đang bận không, ngoài
  /// việc ad đã sẵn sàng hay chưa. Không side effect (không tự load(), không tiêu thụ ad cache).
  Future<bool> canShow() => _hostApi.canShow(placementId);

  Future<AdState> awaitReady(Duration timeout) {
    load();
    return awaitTerminalAdState(state, timeout);
  }

  Future<void> show({
    void Function()? onShown,
    void Function()? onDismissed,
    void Function(AdFlowError error)? onFailedToShow,
    void Function(BlockReason reason)? onBlocked,
  }) => showEventFuture(
    placementId,
    onAdShown: onShown,
    onAdDismissed: onDismissed,
    onAdFailedToShow: onFailedToShow,
    onShowBlocked: onBlocked,
    invokeShow: _hostApi.show,
  );
}
