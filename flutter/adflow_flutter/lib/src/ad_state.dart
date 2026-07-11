import 'dart:async';

import 'package:flutter/foundation.dart';

import 'generated/adflow_api.g.dart';
import 'types.dart';

/// Trạng thái vòng đời của 1 placement - mirror `AdState.kt` (adflow-core), đẩy từ Kotlin qua
/// `AdFlowFlutterApi.onAdState` mỗi khi đổi (xem `AdFlowDispatcher`). Quan sát qua
/// `AdFlow.<type>(id).state` (full-screen) hoặc để `AdFlowBanner`/`AdFlowNative` tự quan sát -
/// không widget/handle nào trong package này poll.
sealed class AdState {
  const AdState();
}

/// Đã đăng ký nhưng chưa có lượt load nào chạy (hoặc placement đang bị chặn bởi gate).
class AdIdle extends AdState {
  const AdIdle();
}

/// Một lượt load (có thể gồm nhiều chu kỳ waterfall + retry) đang chạy.
class AdLoading extends AdState {
  const AdLoading();
}

/// Đã có ad sẵn sàng trong cache.
class AdLoaded extends AdState {
  const AdLoaded(this.loadedAtMs);
  final int loadedAtMs;
}

/// Một chu kỳ waterfall vừa thất bại (toàn bộ ad unit no-fill).
///
/// [willRetry] = true: engine sẽ tự thử lại sau [nextRetryDelayMs] - có thể dismiss slot ngay
/// nhưng vẫn nên sẵn sàng nhận [AdLoaded] muộn. [willRetry] = false: lượt load này đã dùng hết
/// [RetryPolicy.maxRetries] chu kỳ và dừng thật sự - sẽ không có thêm request nền nào cho tới khi
/// có nhu cầu mới (view attach lại, show() self-heal, load()/reload() thủ công, app quay lại
/// foreground với placement preload).
class AdFailed extends AdState {
  const AdFailed({
    required this.error,
    required this.willRetry,
    this.nextRetryDelayMs,
  });
  final AdFlowError error;
  final bool willRetry;
  final int? nextRetryDelayMs;
}

/// Ad đang hiển thị trên màn hình (chỉ dùng cho các loại full-screen).
class AdShowing extends AdState {
  const AdShowing();
}

extension AdStateX on AdState {
  bool get isLoaded => this is AdLoaded;
}

/// Dùng nội bộ bởi [AdFlowDispatcher] khi nhận `onAdState` từ Kotlin - không export ra public API.
AdState adStateFromPigeon(PAdState s) => switch (s.kind) {
  PAdStateKind.idle => const AdIdle(),
  PAdStateKind.loading => const AdLoading(),
  PAdStateKind.loaded => AdLoaded(s.loadedAtMs!),
  PAdStateKind.failed => AdFailed(
    error: s.error!,
    willRetry: s.willRetry!,
    nextRetryDelayMs: s.nextRetryDelayMs,
  ),
  PAdStateKind.showing => const AdShowing(),
};

/// Chờ tới khi [state] thành [AdLoaded] hoặc [AdFailed] (1 lượt load kết thúc, dù thành hay bại),
/// tối đa [timeout] - dùng cho pattern splash: chờ 1 khoảng ngắn rồi show() dù ready hay chưa (xem
/// `InterstitialAdHandle.awaitReady`). Hết [timeout] mà vẫn chưa xong (còn Idle/Loading) thì trả
/// về state hiện tại nguyên trạng, không throw.
Future<AdState> awaitTerminalAdState(
  ValueListenable<AdState> state,
  Duration timeout,
) {
  bool isTerminal(AdState s) => s is AdLoaded || s is AdFailed;

  if (isTerminal(state.value)) return Future.value(state.value);

  final completer = Completer<AdState>();
  late final Timer timer;
  late final VoidCallback listener;

  void finish(AdState s) {
    timer.cancel();
    state.removeListener(listener);
    if (!completer.isCompleted) completer.complete(s);
  }

  listener = () {
    if (isTerminal(state.value)) finish(state.value);
  };
  timer = Timer(timeout, () => finish(state.value));

  state.addListener(listener);
  return completer.future;
}
