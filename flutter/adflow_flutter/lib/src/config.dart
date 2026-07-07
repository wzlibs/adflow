import 'generated/adflow_api.g.dart';

/// Mirror của `RetryPolicy.kt` (adflow-core) - default giống hệt bản Kotlin.
class RetryPolicy {
  const RetryPolicy({
    this.initialDelayMs = 5000,
    this.multiplier = 2.0,
    this.maxDelayMs = 60000,
    this.maxRetries = 5,
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

/// Mirror của `ShowIntervalConfig.kt` - default giống hệt bản Kotlin. Truyền vào
/// `AdFlowCore.initialize()` để tuỳ chỉnh khoảng nghỉ tối thiểu giữa các lần hiển thị
/// Interstitial/App Open.
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

/// Mirror của `PlacementConfig.kt` - KHÔNG có loadRule/showRule: `AdRule` là callback đồng bộ,
/// không bridge qua Platform Channel được (round-trip latency, gọi quá thường xuyên). Dùng
/// `setEnabled()` trên từng `AdFlow*Ad` facade để tắt/bật load-show thay cho AdRule (vd tắt ads
/// cho user premium) - logic gating phức tạp hơn (cooldown, theo giờ...) phải tự viết ở tầng Dart.
class PlacementConfig {
  const PlacementConfig({
    required this.placementId,
    required this.adUnitIds,
    this.enabled = true,
    this.preloadEnabled = true,
    this.retryPolicy = const RetryPolicy(),
    this.expiryMs = 4 * 60 * 60 * 1000,
  });

  final String placementId;
  final List<String> adUnitIds;
  final bool enabled;
  final bool preloadEnabled;
  final RetryPolicy retryPolicy;
  final int expiryMs;

  PPlacementConfig toPigeon() => PPlacementConfig(
        placementId: placementId,
        enabled: enabled,
        preloadEnabled: preloadEnabled,
        adUnitIds: adUnitIds,
        retryPolicy: retryPolicy.toPigeon(),
        expiryMs: expiryMs,
      );
}
