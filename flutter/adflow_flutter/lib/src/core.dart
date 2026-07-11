import 'ad_flow_dispatcher.dart';
import 'app_open_ad.dart';
import 'banner_ad.dart';
import 'generated/adflow_api.g.dart';
import 'interstitial_ad.dart';
import 'native_ad.dart';
import 'placements.dart';
import 'rewarded_ad.dart';
import 'types.dart';

/// Entry point for configuring and accessing AdFlow placements.
class AdFlow {
  AdFlow._();

  static final AdFlowHostApi _hostApi = AdFlowHostApi();
  static bool _initialized = false;

  static Future<void> initialize({
    required List<Placement> placements,
    ShowIntervalConfig showInterval = const ShowIntervalConfig(),
    bool useLogcatLogger = true,
    DebugGeography? consentDebugGeography,
    List<String> consentDebugTestDeviceHashedIds = const [],
  }) async {
    if (_initialized) return;
    _initialized = true;
    AdFlowDispatcher.instance.ensureSetUp();
    await _hostApi.initialize(
      placements.map((placement) => placement.toPigeon()).toList(),
      showInterval.toPigeon(),
      useLogcatLogger,
      consentDebugGeography,
      consentDebugTestDeviceHashedIds,
    );
    await _hostApi.addRevenueLogger();
  }

  static Future<void> setAdsEnabled(bool enabled) =>
      _hostApi.setAdsEnabled(enabled);

  static bool get isShowingFullScreenAd =>
      AdFlowDispatcher.instance.isShowingFullScreenAd;

  static void addRevenueLogger(void Function(AdRevenueEvent event) listener) {
    AdFlowDispatcher.instance.addRevenueListener(listener);
  }

  static AdFlowInterstitialAd interstitial(String placementId) =>
      AdFlowInterstitialAd(placementId);
  static AdFlowAppOpenAd appOpen(String placementId) =>
      AdFlowAppOpenAd(placementId);
  static AdFlowRewardedAd rewarded(String placementId) =>
      AdFlowRewardedAd(placementId);
  static AdFlowBannerAd banner(String placementId) =>
      AdFlowBannerAd(placementId);
  static AdFlowNativeAd native(String placementId) =>
      AdFlowNativeAd(placementId);

  static Future<ConsentStatus> getConsentStatus() =>
      _hostApi.getConsentStatus();
  static Future<PrivacyOptionsRequirement> getPrivacyOptionsRequirement() =>
      _hostApi.getPrivacyOptionsRequirement();
  static Future<bool> canRequestAds() => _hostApi.canRequestAds();
  static Future<AdFlowError?> requestConsentIfNeeded() =>
      _hostApi.requestConsentIfNeeded();
  static Future<AdFlowError?> showPrivacyOptionsForm() =>
      _hostApi.showPrivacyOptionsForm();
}
