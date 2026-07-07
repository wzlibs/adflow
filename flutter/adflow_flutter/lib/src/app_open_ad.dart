import 'config.dart';
import 'generated/adflow_api.g.dart';
import 'show_event_support.dart';

/// Facade Dart cho 1 placement App Open. Giống [AdFlowInterstitialAd] về load/show, thêm
/// [enableAutoShowOnForeground]/[disableAutoShowOnForeground] wrap `AppOpenAdController` thật phía
/// Kotlin - tự show khi app quay lại foreground (ProcessLifecycleOwner), độc lập với vòng đời
/// Flutter engine.
class AdFlowAppOpenAd {
  AdFlowAppOpenAd(PlacementConfig config) : placementId = config.placementId {
    _hostApi.create(config.toPigeon());
  }

  final String placementId;
  static final AppOpenAdHostApi _hostApi = AppOpenAdHostApi();

  Future<bool> get isReady => _hostApi.isReady(placementId);

  Future<PLoadResult> load() => _hostApi.load(placementId);

  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);

  Future<void> enableAutoShowOnForeground() => _hostApi.startAutoShowOnForeground(placementId);

  Future<void> disableAutoShowOnForeground() => _hostApi.stopAutoShowOnForeground(placementId);

  Future<void> show({
    void Function()? onAdShown,
    void Function()? onAdDismissed,
    void Function(PAdFlowError error)? onAdFailedToShow,
    void Function(PBlockReason reason)? onShowBlocked,
  }) =>
      showEventFuture(
        placementId,
        onAdShown: onAdShown,
        onAdDismissed: onAdDismissed,
        onAdFailedToShow: onAdFailedToShow,
        onShowBlocked: onShowBlocked,
        invokeShow: _hostApi.show,
      );
}
