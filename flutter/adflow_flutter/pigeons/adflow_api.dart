import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/generated/adflow_api.g.dart',
    kotlinOut:
        'android/src/main/kotlin/com/adflow/adflow_flutter/generated/AdflowApi.g.kt',
    kotlinOptions: KotlinOptions(
      package: 'com.adflow.adflow_flutter.generated',
    ),
    dartPackageName: 'adflow_flutter',
  ),
)
// Bridge Dart <-> Kotlin cho AdFlow. Schema này bám 1:1 vào public API state-first của
// adflow-core/adflow-admob v2 (xem adflow-core/src/main/java/com/adflow/core/*.kt): mỗi placement
// khai báo tập trung 1 lần qua AdFlowHostApi.initialize(), sau đó thao tác theo placementId qua
// AdHostApi - registry native (AdFlow) tự biết mỗi id thuộc loại nào. Không có field
// loadRule/showRule trong PPlacementConfig (AdRule là callback đồng bộ, không bridge qua channel
// được) - việc bật/tắt ads đi qua AdFlowHostApi.setAdsEnabled() toàn cục thay vì per-placement.
enum PAdType { interstitial, appOpen, rewarded, native, banner }

// Khớp ConsentStatus.kt / PrivacyOptionsRequirement.kt (adflow-core) - case tên trùng hệt nên map
// bằng .valueOf(name) phía Kotlin, không cần when thủ công (xem PigeonMappers.kt).
enum PConsentStatus { unknown, notRequired, required, obtained }

enum PPrivacyOptionsRequirement { unknown, notRequired, required }

// Không có enum core tương ứng - chỉ là Int constant ConsentDebugSettings.DebugGeography bên UMP,
// map thủ công phía Kotlin (xem PigeonMappers.kt).
enum PDebugGeography { disabled, eea, notEea }

// Khớp BlockReason.kt v2: STILL_LOADING, NO_AD_AVAILABLE, CONSENT_REQUIRED, RULE_REJECTED,
// INTERVAL_NOT_ELAPSED, ANOTHER_AD_SHOWING.
enum PBlockReason {
  stillLoading,
  noAdAvailable,
  consentRequired,
  ruleRejected,
  intervalNotElapsed,
  anotherAdShowing,
}

// Gộp chung sự kiện của FullScreenCallback (Interstitial/AppOpen) và RewardedAdCallback - 2
// interface Kotlin khác nhau nhưng tập sự kiện thực tế trùng nhau 4/5 (chỉ rewarded có thêm
// userEarnedReward).
enum PShowEventKind {
  shown,
  failedToShow,
  dismissed,
  showBlocked,
  userEarnedReward,
}

// Khớp AdState.kt (sealed class) v2: Idle/Loading/Loaded/Failed/Showing. loadedAtMs chỉ có ở
// Loaded; error/willRetry/nextRetryDelayMs chỉ có ở Failed.
enum PAdStateKind { idle, loading, loaded, failed, showing }

class PAdState {
  PAdState({
    required this.kind,
    this.loadedAtMs,
    this.error,
    this.willRetry,
    this.nextRetryDelayMs,
  });

  PAdStateKind kind;
  int? loadedAtMs;
  PAdFlowError? error;
  bool? willRetry;
  int? nextRetryDelayMs;
}

// Khớp BannerSize.kt (adflow-admob): BANNER, LARGE_BANNER, MEDIUM_RECTANGLE, ADAPTIVE.
enum PBannerSize { banner, largeBanner, mediumRectangle, adaptive }

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

// Không có loadRule/showRule - xem ghi chú đầu file. Field chỉ áp dụng cho 1 số adType được
// Kotlin bỏ qua khi không liên quan (vd bannerSize chỉ đọc khi adType == banner) - Pigeon không hỗ
// trợ union theo adType nên gộp phẳng, tương tự cách google_mobile_ads gộp AdRequest.
class PPlacementConfig {
  PPlacementConfig({
    required this.placementId,
    required this.adType,
    required this.adUnitIds,
    required this.preload,
    required this.retryPolicy,
    this.expiryMs,
    this.autoShowOnForeground = false,
    this.bannerSize,
    this.rendererId,
  });

  String placementId;
  PAdType adType;
  List<String> adUnitIds;
  bool preload;
  PRetryPolicy retryPolicy;

  /// null = dùng mặc định của native DSL cho loại quảng cáo này. Không áp dụng cho banner.
  int? expiryMs;

  /// Chỉ áp dụng cho adType == appOpen.
  bool autoShowOnForeground;

  /// Chỉ áp dụng cho adType == banner. null = ADAPTIVE (mặc định DSL native).
  PBannerSize? bannerSize;

  /// Chỉ áp dụng cho adType == native. null = renderer mặc định đã đăng ký phía Kotlin (xem
  /// AdflowFlutterPlugin.registerNativeAdRenderer).
  String? rendererId;
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
abstract class AdFlowHostApi {
  /// Khai báo toàn bộ placement 1 lần (mirror AdFlow.initialize {} DSL native v2). Gọi lại lần
  /// nữa là lỗi (fail-fast, giống PlacementRegistry native) - không hỗ trợ khai báo tăng dần.
  /// [consentDebugGeography]/[consentDebugTestDeviceHashedIds] chỉ dùng để test flow EEA (xem
  /// README), để null/rỗng cho production - set 1 lần ở đây vì `consentDebug {}` của native v2 chỉ
  /// cấu hình được lúc `AdFlow.initialize {}`, không nhận theo từng lệnh requestConsentIfNeeded().
  void initialize(
    List<PPlacementConfig> placements,
    PShowIntervalConfig showIntervalConfig,
    bool useLogcatLogger,
    PDebugGeography? consentDebugGeography,
    List<String> consentDebugTestDeviceHashedIds,
  );

  /// Bật/tắt toàn bộ ads (vd premium user) - thay cho setEnabled() per-placement đã bỏ ở native
  /// v2. Khi tắt, load()/show() mọi placement bị chặn ngay (BlockReason.ruleRejected), không chạm
  /// network thật. Khi bật lại, tự kích load() lại cho mọi placement (demand-driven).
  void setAdsEnabled(bool enabled);

  /// Đăng ký đúng 1 RevenueLoggerBridge phía Kotlin, forward sự kiện qua
  /// [AdFlowFlutterApi.onRevenuePaid]. Gọi lại nhiều lần là no-op (idempotent).
  void addRevenueLogger();

  // GDPR/consent (xem ConsentManager.kt) - 1 khái niệm toàn app (singleton), không phải
  // per-placement nên gộp vào đây thay vì 1 HostApi riêng.
  PConsentStatus getConsentStatus();

  PPrivacyOptionsRequirement getPrivacyOptionsRequirement();

  bool canRequestAds();

  /// Xin consent nếu cần - no-op nếu ngoài khu vực cần (EEA/UK). Không cần tham số Activity -
  /// Kotlin tự lấy currentActivity (ActivityAware).
  @async
  PAdFlowError? requestConsentIfNeeded();

  /// Cho user xem lại/đổi consent đã có - chỉ nên gọi khi getPrivacyOptionsRequirement() ==
  /// PPrivacyOptionsRequirement.required.
  @async
  PAdFlowError? showPrivacyOptionsForm();
}

/// API chung cho mọi loại placement theo id - Kotlin tự tra placementId thuộc loại nào qua
/// registry native (AdFlow.interstitial/appOpen/rewarded/native đã đăng ký lúc initialize()) nên
/// không cần 1 HostApi riêng/loại như trước. show()/reload() no-op (log lỗi) nếu gọi sai loại (vd
/// show() 1 banner) - Dart chặn trước bằng type system (mỗi handle chỉ lộ method hợp lệ).
///
/// load()/reload() fire-and-forget - core v2 KHÔNG có callback kết quả cho load() nữa (khác v1):
/// theo dõi tiến trình qua [AdFlowFlutterApi.onAdState] (Loading -> Loaded/Failed), không có gì để
/// await đồng bộ ở đây.
@HostApi()
abstract class AdHostApi {
  void load(String placementId);

  /// Chỉ có ý nghĩa với native ad (ép fetch ad MỚI dù cache còn hạn) - no-op với loại khác.
  void reload(String placementId);

  /// Chỉ có ý nghĩa với interstitial/appOpen/rewarded - kết quả trả qua
  /// [AdFlowFlutterApi.onShowEvent], không phải qua đây.
  void show(String placementId);
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

  /// Đẩy mỗi khi `StateFlow<AdState>` của placement đổi (subscribe lúc initialize()) - nền tảng cho
  /// `handle.state` (full-screen) và AdFlowBanner/AdFlowNative (widget reactive, không poll).
  void onAdState(String placementId, PAdState state);
}
