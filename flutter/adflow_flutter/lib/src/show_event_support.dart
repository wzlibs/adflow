import 'dart:async';

import 'flutter_api_dispatcher.dart';
import 'generated/adflow_api.g.dart';

/// Dùng chung bởi `show()` của Interstitial/AppOpen/Rewarded: đăng ký 1 handler nhận mọi
/// `onShowEvent` cho [placementId], gọi [invokeShow] để bắt đầu, rồi trả về 1 Future chỉ hoàn tất
/// khi gặp sự kiện kết thúc thật sự (`dismissed`/`failedToShow`/`showBlocked`) - `shown` và
/// `userEarnedReward` chỉ là tín hiệu giữa chừng, không kết thúc show().
Future<void> showEventFuture(
  String placementId, {
  void Function()? onAdShown,
  void Function()? onAdDismissed,
  void Function(PAdFlowError error)? onAdFailedToShow,
  void Function(PBlockReason reason)? onShowBlocked,
  void Function(PRewardItem reward)? onUserEarnedReward,
  required void Function(String placementId) invokeShow,
}) {
  final completer = Completer<void>();

  void finish() {
    FlutterApiDispatcher.instance.unregisterShowEventHandler(placementId);
    if (!completer.isCompleted) completer.complete();
  }

  FlutterApiDispatcher.instance.registerShowEventHandler(placementId, (
    kind,
    error,
    blockReason,
    reward,
  ) {
    switch (kind) {
      case PShowEventKind.shown:
        onAdShown?.call();
      case PShowEventKind.userEarnedReward:
        if (reward != null) onUserEarnedReward?.call(reward);
      case PShowEventKind.dismissed:
        onAdDismissed?.call();
        finish();
      case PShowEventKind.failedToShow:
        if (error != null) onAdFailedToShow?.call(error);
        finish();
      case PShowEventKind.showBlocked:
        if (blockReason != null) onShowBlocked?.call(blockReason);
        finish();
    }
  });

  invokeShow(placementId);
  return completer.future;
}
