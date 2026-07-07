import 'generated/adflow_api.g.dart';

/// Callback nhận sự kiện show() thô cho 1 placement - dùng nội bộ bởi [showEventFuture].
typedef ShowEventHandler = void Function(
  PShowEventKind kind,
  PAdFlowError? error,
  PBlockReason? blockReason,
  PRewardItem? reward,
);

/// Implement duy nhất của [AdFlowFlutterApi] cho toàn bộ package - định tuyến sự kiện
/// `onShowEvent` tới đúng handler đã đăng ký theo `placementId` (mỗi `AdFlow*Ad.show()` tự đăng
/// ký/gỡ đăng ký handler của chính nó), và phát `onRevenuePaid` cho mọi listener đã đăng ký qua
/// `AdFlowCore.addRevenueLogger()`.
class FlutterApiDispatcher implements AdFlowFlutterApi {
  FlutterApiDispatcher._();

  static final FlutterApiDispatcher instance = FlutterApiDispatcher._();

  final Map<String, ShowEventHandler> _showEventHandlers = {};
  final List<void Function(PAdRevenueEvent)> _revenueListeners = [];

  bool _isSetUp = false;

  /// Idempotent - an toàn khi gọi từ nhiều `AdFlowCore.initialize()` (dù initialize() tự guard
  /// chỉ chạy 1 lần rồi).
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
}
