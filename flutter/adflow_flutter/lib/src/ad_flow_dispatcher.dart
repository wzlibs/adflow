import 'package:flutter/foundation.dart';

import 'ad_state.dart';
import 'generated/adflow_api.g.dart';

/// Callback nhận sự kiện show() thô cho 1 placement - dùng nội bộ bởi `show_event_support.dart`.
typedef ShowEventHandler =
    void Function(
      PShowEventKind kind,
      PAdFlowError? error,
      PBlockReason? blockReason,
      PRewardItem? reward,
    );

/// Implement duy nhất của [AdFlowFlutterApi] cho toàn bộ package:
/// - `onShowEvent` định tuyến tới đúng handler theo `placementId` (mỗi `show()` tự đăng ký/gỡ
///   đăng ký handler của chính nó, xem `show_event_support.dart`).
/// - `onAdState` cập nhật 1 `ValueNotifier<AdState>` theo `placementId` - nền tảng cho
///   `AdFlow.<type>(id).state` và widget `AdFlowBanner`/`AdFlowNative` (quan sát trực tiếp, không
///   poll, không cần bump `Key`).
/// - `onRevenuePaid` phát cho mọi listener đã đăng ký qua `AdFlow.addRevenueLogger()`.
class AdFlowDispatcher implements AdFlowFlutterApi {
  AdFlowDispatcher._();

  static final AdFlowDispatcher instance = AdFlowDispatcher._();

  final Map<String, ShowEventHandler> _showEventHandlers = {};
  final List<void Function(PAdRevenueEvent)> _revenueListeners = [];
  final Map<String, ValueNotifier<AdState>> _stateNotifiers = {};

  bool _isSetUp = false;

  /// Idempotent - an toàn khi gọi từ nhiều `AdFlow.initialize()` (dù initialize() tự guard chỉ
  /// chạy 1 lần rồi).
  void ensureSetUp() {
    if (_isSetUp) return;
    _isSetUp = true;
    AdFlowFlutterApi.setUp(this);
  }

  void registerShowEventHandler(String placementId, ShowEventHandler handler) {
    _showEventHandlers[placementId] = handler;
  }

  void unregisterShowEventHandler(String placementId) {
    _showEventHandlers.remove(placementId);
  }

  void addRevenueListener(void Function(PAdRevenueEvent event) listener) {
    _revenueListeners.add(listener);
  }

  /// Chưa nhận `onAdState` lần nào cho [placementId] (vừa `initialize()` xong, trước lượt push đầu
  /// tiên từ Kotlin) mặc định [AdIdle] - khớp `AdState.Idle` phía native trước khi có lượt load
  /// nào. Cùng 1 instance dùng chung cho mọi lệnh gọi `AdFlow.<type>(id)` với cùng placementId.
  ValueListenable<AdState> stateOf(String placementId) => _stateNotifiers
      .putIfAbsent(placementId, () => ValueNotifier(const AdIdle()));

  /// true nếu có bất kỳ placement full-screen nào (đã biết qua [onAdState]) đang ở [AdShowing] -
  /// suy ra từ state đã stream, không có lệnh gọi Kotlin riêng (native v2 không lộ khái niệm này
  /// ra public API, chỉ dùng nội bộ cho việc chống hiển thị chồng full-screen ad).
  bool get isShowingFullScreenAd =>
      _stateNotifiers.values.any((n) => n.value is AdShowing);

  @override
  void onRevenuePaid(PAdRevenueEvent event) {
    for (final listener in _revenueListeners) {
      listener(event);
    }
  }

  @override
  void onShowEvent(
    String placementId,
    PShowEventKind kind,
    PAdFlowError? error,
    PBlockReason? blockReason,
    PRewardItem? reward,
  ) {
    _showEventHandlers[placementId]?.call(kind, error, blockReason, reward);
  }

  @override
  void onAdState(String placementId, PAdState state) {
    _stateNotifiers
        .putIfAbsent(placementId, () => ValueNotifier(const AdIdle()))
        .value = adStateFromPigeon(
      state,
    );
  }
}
