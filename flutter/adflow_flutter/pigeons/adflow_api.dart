import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/generated/adflow_api.g.dart',
    kotlinOut:
        'android/src/main/kotlin/com/adflow/adflow_flutter/generated/AdflowApi.g.kt',
    kotlinOptions: KotlinOptions(package: 'com.adflow.adflow_flutter.generated'),
    dartPackageName: 'adflow_flutter',
  ),
)
// Bridge Dart <-> Kotlin cho AdFlow. Schema này bám 1:1 vào public API thật của
// adflow-core/adflow-admob (xem adflow-core/src/main/java/com/adflow/core/*.kt) - KHÔNG có field
// loadRule/showRule trong PPlacementConfig (AdRule là callback đồng bộ, không bridge qua channel
// được - xem README phần thiết kế). Gating on/off phía Dart đi qua setEnabled() riêng ở mỗi HostApi.

enum PAdType { interstitial, appOpen, rewarded, native, banner }

// Khớp ConsentStatus.kt / PrivacyOptionsRequirement.kt (adflow-core) - case tên trùng hệt nên map
// bằng .valueOf(name) phía Kotlin, không cần when thủ công (xem PigeonMappers.kt).
enum PConsentStatus { unknown, notRequired, required, obtained }

enum PPrivacyOptionsRequirement { unknown, notRequired, required }

// Không có enum core tương ứng - chỉ là Int constant ConsentDebugSettings.DebugGeography bên UMP,
// map thủ công phía Kotlin (xem PigeonMappers.kt).
enum PDebugGeography { disabled, eea, notEea }

// Khớp BlockReason.kt: DISABLED, RULE_REJECTED, INTERVAL_NOT_ELAPSED, NOT_READY, ANOTHER_AD_SHOWING.
enum PBlockReason {
  disabled,
  ruleRejected,
  intervalNotElapsed,
  notReady,
  anotherAdShowing,
}

// Gộp chung sự kiện của ShowCallback (Interstitial/AppOpen) và RewardedAdCallback - 2 API Kotlin
// khác nhau nhưng tập sự kiện thực tế trùng nhau 4/5 (chỉ rewarded có thêm userEarnedReward).
enum PShowEventKind {
  shown,
  failedToShow,
  dismissed,
  showBlocked,
  userEarnedReward,
}

class PRetryPolicy {
  PRetryPolicy({
    required this.initialDelayMs,
    required this.multiplier,
    required this.maxDelayMs,
    required this.maxRetries,
  });

  int initialDelayMs;
  double multiplier;
  int maxDelayMs;
  int maxRetries;
}

// Không có loadRule/showRule - xem ghi chú đầu file.
class PPlacementConfig {
  PPlacementConfig({
    required this.placementId,
    required this.enabled,
    required this.preloadEnabled,
    required this.adUnitIds,
    required this.retryPolicy,
    required this.expiryMs,
  });

  String placementId;
  bool enabled;
  bool preloadEnabled;
  List<String> adUnitIds;
  PRetryPolicy retryPolicy;
  int expiryMs;
}

class PShowIntervalConfig {
  PShowIntervalConfig({
    required this.interstitialAfterInterstitialMs,
    required this.appOpenAfterAppOpenMs,
    required this.interstitialAfterAppOpenMs,
    required this.appOpenAfterInterstitialMs,
  });

  int interstitialAfterInterstitialMs;
  int appOpenAfterAppOpenMs;
  int interstitialAfterAppOpenMs;
  int appOpenAfterInterstitialMs;
}

class PAdFlowError {
  PAdFlowError({required this.code, required this.message});

  int code;
  String message;
}

class PRewardItem {
  PRewardItem({required this.type, required this.amount});

  String type;
  int amount;
}

class PLoadResult {
  PLoadResult({required this.success, this.error});

  bool success;
  PAdFlowError? error;
}

class PAdRevenueEvent {
  PAdRevenueEvent({
    required this.placementId,
    required this.adType,
    required this.adUnitId,
    required this.valueMicros,
    required this.currencyCode,
    required this.precision,
    this.adNetwork,
  });

  String placementId;
  PAdType adType;
  String adUnitId;
  int valueMicros;
  String currencyCode;
  String precision;
  String? adNetwork;
}

@HostApi()
abstract class AdFlowCoreHostApi {
  void configure(PShowIntervalConfig showIntervalConfig, bool useLogcatLogger);

  @async
  void initializeProvider();

  bool isShowingFullScreenAd();

  /// Đăng ký đúng 1 RevenueLoggerBridge phía Kotlin, forward sự kiện qua
  /// [AdFlowFlutterApi.onRevenuePaid]. Gọi lại nhiều lần là no-op (idempotent).
  void addRevenueLogger();

  // GDPR/consent (xem ConsentManager.kt) - 1 khái niệm toàn app (singleton), không phải
  // per-placement nên gộp vào đây thay vì 1 HostApi riêng.
  PConsentStatus getConsentStatus();

  PPrivacyOptionsRequirement getPrivacyOptionsRequirement();

  bool canRequestAds();

  /// Xin consent nếu cần - no-op nếu ngoài khu vực cần (EEA/UK). [debugGeography]/
  /// [testDeviceHashedIds] chỉ dùng để test flow EEA (xem README), để null/rỗng cho production.
  /// Không cần tham số Activity - Kotlin tự lấy PlacementRegistry.currentActivity.
  @async
  PAdFlowError? requestConsentIfNeeded(
    PDebugGeography? debugGeography,
    List<String> testDeviceHashedIds,
  );

  /// Cho user xem lại/đổi consent đã có - chỉ nên gọi khi getPrivacyOptionsRequirement() ==
  /// PPrivacyOptionsRequirement.required.
  @async
  PAdFlowError? showPrivacyOptionsForm();
}

@HostApi()
abstract class InterstitialAdHostApi {
  void create(PPlacementConfig config);

  bool isReady(String placementId);

  @async
  PLoadResult load(String placementId);

  void show(String placementId);

  /// Bật/tắt runtime override độc lập với PlacementConfig.enabled lúc tạo - dùng thay AdRule
  /// (vd tắt ads cho user premium). Khi tắt, load()/show() bị chặn ngay, trả BlockReason.disabled,
  /// không chạm tới manager thật.
  void setEnabled(String placementId, bool enabled);
}

@HostApi()
abstract class AppOpenAdHostApi {
  void create(PPlacementConfig config);

  bool isReady(String placementId);

  @async
  PLoadResult load(String placementId);

  void show(String placementId);

  void setEnabled(String placementId, bool enabled);

  /// Dựng và start() 1 AppOpenAdController thật cho placement này - tự show khi app quay lại
  /// foreground (ProcessLifecycleOwner), độc lập với vòng đời Flutter engine.
  void startAutoShowOnForeground(String placementId);

  void stopAutoShowOnForeground(String placementId);
}

@HostApi()
abstract class RewardedAdHostApi {
  void create(PPlacementConfig config);

  bool isReady(String placementId);

  @async
  PLoadResult load(String placementId);

  void show(String placementId);

  void setEnabled(String placementId, bool enabled);
}

@HostApi()
abstract class BannerAdHostApi {
  void create(PPlacementConfig config);

  bool isReady(String placementId);

  @async
  PLoadResult load(String placementId);

  void setEnabled(String placementId, bool enabled);

  // Không có show()/getView(): BannerPlatformViewFactory tự tra PlacementRegistry theo
  // placementId để lấy View thật khi Flutter dựng AndroidView.
}

@HostApi()
abstract class NativeAdHostApi {
  void create(PPlacementConfig config);

  bool isReady(String placementId);

  @async
  PLoadResult load(String placementId);

  @async
  PLoadResult reload(String placementId);

  void setEnabled(String placementId, bool enabled);
}

@FlutterApi()
abstract class AdFlowFlutterApi {
  void onRevenuePaid(PAdRevenueEvent event);

  void onShowEvent(
    String placementId,
    PShowEventKind kind,
    PAdFlowError? error,
    PBlockReason? blockReason,
    PRewardItem? reward,
  );
}
