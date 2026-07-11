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
