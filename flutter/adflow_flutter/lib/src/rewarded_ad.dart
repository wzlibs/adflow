import 'config.dart';
import 'generated/adflow_api.g.dart';
import 'show_event_support.dart';

/// Facade Dart cho 1 placement Rewarded. Giống [AdFlowInterstitialAd] nhưng `show()` có thêm
/// [onUserEarnedReward] - gọi giữa chừng (không kết thúc Future) khi user xem xong đủ để nhận
/// thưởng.
class AdFlowRewardedAd {
  AdFlowRewardedAd(PlacementConfig config) : placementId = config.placementId {
    _hostApi.create(config.toPigeon());
  }

  final String placementId;
  static final RewardedAdHostApi _hostApi = RewardedAdHostApi();

  Future<bool> get isReady => _hostApi.isReady(placementId);

  Future<PLoadResult> load() => _hostApi.load(placementId);

  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);

  Future<void> show({
    void Function()? onAdShown,
    void Function()? onAdDismissed,
    void Function(PAdFlowError error)? onAdFailedToShow,
    void Function(PBlockReason reason)? onShowBlocked,
    void Function(PRewardItem reward)? onUserEarnedReward,
  }) =>
      showEventFuture(
        placementId,
        onAdShown: onAdShown,
        onAdDismissed: onAdDismissed,
        onAdFailedToShow: onAdFailedToShow,
        onShowBlocked: onShowBlocked,
        onUserEarnedReward: onUserEarnedReward,
        invokeShow: _hostApi.show,
      );
}
