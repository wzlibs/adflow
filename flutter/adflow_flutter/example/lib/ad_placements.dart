import 'package:adflow_flutter/adflow_flutter.dart';

/// Mirror Dart của `DemoAdPlacements.kt` (app module gốc) - pattern khuyến nghị mà mỗi app tự viết
/// khi tích hợp AdFlow, không phải 1 class có sẵn trong lib. Ad Unit ID dưới đây là test ID chính
/// thức của Google - PHẢI thay bằng ID thật trước khi release (xem README).
class AdPlacements {
  AdPlacements()
      : splashInterstitial = AdFlowInterstitialAd(
          const PlacementConfig(
            placementId: 'splash_interstitial',
            adUnitIds: ['ca-app-pub-3940256099942544/1033173712'],
            preloadEnabled: false
          ),
        ),
        globalInterstitial = AdFlowInterstitialAd(
          const PlacementConfig(
            placementId: 'global_interstitial',
            adUnitIds: ['ca-app-pub-3940256099942544/1033173712'],
          ),
        ),
        appOpen = AdFlowAppOpenAd(
          const PlacementConfig(
            placementId: 'app_open',
            adUnitIds: ['ca-app-pub-3940256099942544/9257395921'],
          ),
        ),
        rewarded = AdFlowRewardedAd(
          const PlacementConfig(
            placementId: 'rewarded',
            adUnitIds: ['ca-app-pub-3940256099942544/5224354917'],
          ),
        ),
        banner = AdFlowBannerAd(
          const PlacementConfig(
            // Test ID banner cố định (fixed-size, 320x50) - khớp với AdSize.BANNER mà
            // AdMobBannerAdManager hard-code phía Kotlin. "9214589741" là test ID Adaptive Banner
            // của Google - hiển thị ad cao hơn 50dp, làm AdFlowBannerAdView (SizedBox height: 50)
            // bị đè chồng lên nội dung phía trên.
            placementId: 'home_banner',
            adUnitIds: ['ca-app-pub-3940256099942544/6300978111'],
          ),
        ),
        native = AdFlowNativeAd(
          const PlacementConfig(
            placementId: 'home_native',
            adUnitIds: ['ca-app-pub-3940256099942544/2247696110'],
          ),
        ),
        // Placement thứ 2, dùng renderer tùy biến 'compactCard' (đăng ký ở MainActivity.kt) - minh
        // hoạ nhiều native ad placement trong cùng 1 app, mỗi placement 1 UI riêng.
        feedNative = AdFlowNativeAd(
          const PlacementConfig(
            placementId: 'feed_native',
            adUnitIds: ['ca-app-pub-3940256099942544/2247696110'],
          ),
        ),
        // Placement thứ 3, dùng renderer có sẵn 'small' (DefaultSmallNativeAdRenderer từ
        // adflow-admob, đăng ký ở MainActivity.kt) - minh hoạ rendererId không nhất thiết phải là
        // renderer tự viết, dùng lại renderer có sẵn trong lib cũng qua đúng 1 cơ chế đó.
        smallNative = AdFlowNativeAd(
          const PlacementConfig(
            placementId: 'small_native',
            adUnitIds: ['ca-app-pub-3940256099942544/2247696110'],
          ),
        );

  final AdFlowInterstitialAd splashInterstitial;
  final AdFlowInterstitialAd globalInterstitial;
  final AdFlowAppOpenAd appOpen;
  final AdFlowRewardedAd rewarded;
  final AdFlowBannerAd banner;
  final AdFlowNativeAd native;
  final AdFlowNativeAd feedNative;
  final AdFlowNativeAd smallNative;

  Future<void> loadAll() => Future.wait([
        splashInterstitial.load(),
        globalInterstitial.load(),
        appOpen.load(),
        rewarded.load(),
        banner.load(),
        native.load(),
        feedNative.load(),
        smallNative.load(),
      ]);

  /// Bật/tắt ads bị ảnh hưởng bởi trạng thái premium - thay cho AdRule (không bridge qua channel
  /// được). Rewarded không bị ảnh hưởng, giống demo Kotlin gốc (rewarded luôn hiện, user chủ động
  /// xem để nhận thưởng).
  Future<void> setPremium(bool isPremium) {
    final enabled = !isPremium;
    return Future.wait([
      splashInterstitial.setEnabled(enabled),
      globalInterstitial.setEnabled(enabled),
      appOpen.setEnabled(enabled),
      banner.setEnabled(enabled),
      native.setEnabled(enabled),
      feedNative.setEnabled(enabled),
      smallNative.setEnabled(enabled),
    ]);
  }
}
