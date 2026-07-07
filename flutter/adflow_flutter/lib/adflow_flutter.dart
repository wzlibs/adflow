// Facade Dart API cho AdFlow - Android-only ở giai đoạn 1 (xem README). Không dùng
// plugin_platform_interface/MethodChannel pattern liên bang vì hiện chỉ có 1 nền tảng; bridge thật
// sự đi qua Pigeon (xem pigeons/adflow_api.dart và lib/src/generated/adflow_api.g.dart).

export 'src/app_open_ad.dart';
export 'src/banner_ad.dart';
export 'src/config.dart';
export 'src/core.dart';
export 'src/interstitial_ad.dart';
export 'src/native_ad.dart';
export 'src/rewarded_ad.dart';
// Kiểu dữ liệu Pigeon-sinh (P-prefix) dùng trực tiếp làm public API cho các giá trị đơn giản
// (error/reward/revenue event/enum) - không cần 1 lớp Dart mirror riêng như PlacementConfig, vì
// không có field nào cần che giấu ở đây.
export 'src/generated/adflow_api.g.dart'
    show
        PAdFlowError,
        PAdRevenueEvent,
        PAdType,
        PBlockReason,
        PLoadResult,
        PRewardItem,
        PShowEventKind;
