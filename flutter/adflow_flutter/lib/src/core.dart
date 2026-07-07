import 'config.dart';
import 'flutter_api_dispatcher.dart';
import 'generated/adflow_api.g.dart';

/// Entry point cấu hình toàn cục của AdFlow.
class AdFlowCore {
  AdFlowCore._();

  static final AdFlowCoreHostApi _hostApi = AdFlowCoreHostApi();
  static bool _initialized = false;

  /// Khởi tạo AdFlow - gọi đúng 1 lần trong `main()`, sau `WidgetsFlutterBinding.ensureInitialized()`.
  ///
  /// Không đi qua cơ chế `AdFlowCore.runOnFirstForeground` phía native nữa: `main()` của 1 app
  /// Flutter chạy về cơ bản đã đồng nghĩa app sắp có UI - khác `Application.onCreate()` thuần
  /// Android, nơi process có thể được OS đánh thức chỉ để xử lý việc nền (vd FCM push) mà không hề
  /// có Activity nào sắp hiển thị. Đây là đơn giản hoá có chủ đích: app nhúng Flutter theo kiểu
  /// add-to-app (Dart entrypoint không đảm bảo luôn foreground) cần tự gate lời gọi này sau khi
  /// biết chắc app đang ở foreground.
  static Future<void> initialize({
    ShowIntervalConfig showIntervalConfig = const ShowIntervalConfig(),
    bool useLogcatLogger = true,
  }) async {
    if (_initialized) return;
    _initialized = true;
    FlutterApiDispatcher.instance.ensureSetUp();
    await _hostApi.configure(showIntervalConfig.toPigeon(), useLogcatLogger);
    await _hostApi.initializeProvider();
    await _hostApi.addRevenueLogger();
  }

  static Future<bool> get isShowingFullScreenAd => _hostApi.isShowingFullScreenAd();

  /// Đăng ký lắng nghe sự kiện doanh thu (AdMob paid event) để forward sang Adjust/Firebase/...
  /// Có thể gọi nhiều lần để thêm nhiều listener độc lập.
  static void addRevenueLogger(void Function(PAdRevenueEvent event) onRevenuePaid) {
    FlutterApiDispatcher.instance.addRevenueListener(onRevenuePaid);
  }
}
