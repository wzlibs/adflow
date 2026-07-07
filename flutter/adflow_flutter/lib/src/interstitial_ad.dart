import 'config.dart';
import 'generated/adflow_api.g.dart';
import 'show_event_support.dart';

/// Facade Dart cho 1 placement Interstitial. Constructor gọi `create()` ngay - idempotent phía
/// Kotlin theo `placementId`, nên tạo lại instance Dart cho cùng placementId (hot reload...) không
/// tạo ra 1 manager thật thứ 2.
class AdFlowInterstitialAd {
  AdFlowInterstitialAd(PlacementConfig config) : placementId = config.placementId {
    _hostApi.create(config.toPigeon());
  }

  final String placementId;
  static final InterstitialAdHostApi _hostApi = InterstitialAdHostApi();

  Future<bool> get isReady => _hostApi.isReady(placementId);

  Future<PLoadResult> load() => _hostApi.load(placementId);

  /// Bật/tắt ads cho placement này (thay cho AdRule - vd tắt khi user là premium).
  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);

  /// Hiển thị quảng cáo; Future hoàn tất khi show() thực sự kết thúc (dismissed/failedToShow/
  /// showBlocked) - `onAdShown` chỉ là tín hiệu giữa chừng, không kết thúc Future.
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
