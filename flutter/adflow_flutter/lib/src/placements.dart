import 'generated/adflow_api.g.dart';
import 'types.dart';

const _defaultExpiryMs =
    4 * 60 * 60 * 1000; // 4 giờ - khớp mặc định DSL native v2.

/// Mirror `RetryPolicy.kt` (adflow-core) - default khớp bản Kotlin: HỮU HẠN 3 chu kỳ (backoff
/// 5s/10s/20s) rồi dừng thật sự (`AdFailed.willRetry == false`), không retry vô hạn nền. Xem
/// `AdState.dart`/`AdFailed` - placement không kẹt chết ở Failed vì 1 lượt load mới tự mở khi có
/// nhu cầu thật.
class RetryPolicy {
  const RetryPolicy({
    this.initialDelayMs = 5000,
    this.multiplier = 2.0,
    this.maxDelayMs = 60000,
    this.maxRetries = 3,
  });

  final int initialDelayMs;
  final double multiplier;
  final int maxDelayMs;
  final int maxRetries;

  PRetryPolicy toPigeon() => PRetryPolicy(
    initialDelayMs: initialDelayMs,
    multiplier: multiplier,
    maxDelayMs: maxDelayMs,
    maxRetries: maxRetries,
  );
}

/// Mirror `ShowIntervalConfig.kt` - khoảng nghỉ tối thiểu giữa các lần hiển thị Interstitial/App
/// Open, tính từ lúc ad TRƯỚC được đóng.
class ShowIntervalConfig {
  const ShowIntervalConfig({
    this.interstitialAfterInterstitialMs = 30000,
    this.appOpenAfterAppOpenMs = 30000,
    this.interstitialAfterAppOpenMs = 6000,
    this.appOpenAfterInterstitialMs = 6000,
  });

  final int interstitialAfterInterstitialMs;
  final int appOpenAfterAppOpenMs;
  final int interstitialAfterAppOpenMs;
  final int appOpenAfterInterstitialMs;

  PShowIntervalConfig toPigeon() => PShowIntervalConfig(
    interstitialAfterInterstitialMs: interstitialAfterInterstitialMs,
    appOpenAfterAppOpenMs: appOpenAfterAppOpenMs,
    interstitialAfterAppOpenMs: interstitialAfterAppOpenMs,
    appOpenAfterInterstitialMs: appOpenAfterInterstitialMs,
  );
}

/// Khai báo 1 placement - truyền vào `AdFlow.initialize(placements: [...])` (mirror
/// `AdFlow.initialize {}` DSL native v2, tập trung tất cả placement 1 chỗ). KHÔNG có `enabled`
/// (native v2 đã bỏ) - dùng `AdFlow.setAdsEnabled()` toàn cục để tắt/bật hết ads (vd user premium).
sealed class Placement {
  const Placement(this.placementId);

  final String placementId;

  PPlacementConfig toPigeon();
}

class InterstitialPlacement extends Placement {
  const InterstitialPlacement(
    super.placementId, {
    required this.adUnits,
    this.preload = true,
    this.expiryMs = _defaultExpiryMs,
    this.retryPolicy = const RetryPolicy(),
  });

  final List<String> adUnits;
  final bool preload;
  final int expiryMs;
  final RetryPolicy retryPolicy;

  @override
  PPlacementConfig toPigeon() => PPlacementConfig(
    placementId: placementId,
    adType: PAdType.interstitial,
    adUnitIds: adUnits,
    preload: preload,
    retryPolicy: retryPolicy.toPigeon(),
    expiryMs: expiryMs,
    autoShowOnForeground: false,
  );
}

class RewardedPlacement extends Placement {
  const RewardedPlacement(
    super.placementId, {
    required this.adUnits,
    this.preload = true,
    this.expiryMs = _defaultExpiryMs,
    this.retryPolicy = const RetryPolicy(),
  });

  final List<String> adUnits;
  final bool preload;
  final int expiryMs;
  final RetryPolicy retryPolicy;

  @override
  PPlacementConfig toPigeon() => PPlacementConfig(
    placementId: placementId,
    adType: PAdType.rewarded,
    adUnitIds: adUnits,
    preload: preload,
    retryPolicy: retryPolicy.toPigeon(),
    expiryMs: expiryMs,
    autoShowOnForeground: false,
  );
}

/// [autoShowOnForeground] true: native v2 tự show placement này mỗi khi app quay lại foreground
/// (ProcessLifecycleOwner, không bao giờ đè lên full-screen ad khác đang hiển thị) - không cần
/// Dart tự gọi gì thêm sau `AdFlow.initialize()`.
class AppOpenPlacement extends Placement {
  const AppOpenPlacement(
    super.placementId, {
    required this.adUnits,
    this.preload = true,
    this.expiryMs = _defaultExpiryMs,
    this.retryPolicy = const RetryPolicy(),
    this.autoShowOnForeground = false,
  });

  final List<String> adUnits;
  final bool preload;
  final int expiryMs;
  final RetryPolicy retryPolicy;
  final bool autoShowOnForeground;

  @override
  PPlacementConfig toPigeon() => PPlacementConfig(
    placementId: placementId,
    adType: PAdType.appOpen,
    adUnitIds: adUnits,
    preload: preload,
    retryPolicy: retryPolicy.toPigeon(),
    expiryMs: expiryMs,
    autoShowOnForeground: autoShowOnForeground,
  );
}

/// Banner KHÔNG có `expiryMs` - 1 banner được hiển thị và tự làm mới bởi mạng quảng cáo ngay khi
/// load xong, không cache-rồi-show-sau như các loại khác nên không có khái niệm "cũ".
class BannerPlacement extends Placement {
  const BannerPlacement(
    super.placementId, {
    required this.adUnits,
    this.preload = true,
    this.retryPolicy = const RetryPolicy(),
    this.size = BannerSize.adaptive,
  });

  final List<String> adUnits;
  final bool preload;
  final RetryPolicy retryPolicy;
  final BannerSize size;

  @override
  PPlacementConfig toPigeon() => PPlacementConfig(
    placementId: placementId,
    adType: PAdType.banner,
    adUnitIds: adUnits,
    preload: preload,
    retryPolicy: retryPolicy.toPigeon(),
    autoShowOnForeground: false,
    bannerSize: size,
  );
}

class NativePlacement extends Placement {
  const NativePlacement(
    super.placementId, {
    required this.adUnits,
    this.preload = true,
    this.expiryMs = _defaultExpiryMs,
    this.retryPolicy = const RetryPolicy(),
    this.rendererId,
  });

  final List<String> adUnits;
  final bool preload;
  final int expiryMs;
  final RetryPolicy retryPolicy;

  /// Renderer mặc định cho placement này khi widget `AdFlowNative` không tự chỉ định renderer
  /// riêng - null = renderer mặc định đã đăng ký phía Kotlin ("medium", xem
  /// `AdflowFlutterPlugin.registerNativeAdRenderer`).
  final String? rendererId;

  @override
  PPlacementConfig toPigeon() => PPlacementConfig(
    placementId: placementId,
    adType: PAdType.native,
    adUnitIds: adUnits,
    preload: preload,
    retryPolicy: retryPolicy.toPigeon(),
    expiryMs: expiryMs,
    autoShowOnForeground: false,
    rendererId: rendererId,
  );
}
