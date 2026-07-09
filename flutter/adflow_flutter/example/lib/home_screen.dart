import 'package:adflow_flutter/adflow_flutter.dart';
import 'package:flutter/material.dart';

import 'ad_placements.dart';

/// Mirror Dart của `HomeScreen.kt` - poll `isReady` cho banner/native để biết khi nào render
/// [AdFlowBannerAdView]/[AdFlowNativeAdView] (widget đó không tự trigger rebuild khi ad load xong
/// ở background).
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.placements});

  final AdPlacements placements;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _premium = false;
  PRewardItem? _lastReward;
  bool _bannerReady = false;
  bool _nativeReady = false;
  bool _feedNativeReady = false;
  bool _smallNativeReady = false;
  bool _privacyOptionsRequired = false;
  // Bump chỉ khi reload() thành công, dùng làm Key của AdFlowNativeAdView bên dưới để ép Flutter
  // huỷ-tạo lại platform view đó, đọc đúng native ad mới nhất vừa swap xong.
  int _nativeGeneration = 0;

  @override
  void initState() {
    super.initState();
    _pollBannerReady();
    _pollNativeReady();
    _pollFeedNativeReady();
    _pollSmallNativeReady();
    _loadPrivacyOptionsRequirement();
  }

  Future<void> _loadPrivacyOptionsRequirement() async {
    final requirement = await AdFlowCore.getPrivacyOptionsRequirement();
    if (!mounted) return;
    setState(() => _privacyOptionsRequired = requirement == PPrivacyOptionsRequirement.required);
  }

  Future<void> _pollBannerReady() async {
    while (mounted && !_bannerReady) {
      final ready = await widget.placements.banner.isReady;
      if (!mounted) return;
      if (ready) {
        setState(() => _bannerReady = true);
        return;
      }
      await Future.delayed(const Duration(milliseconds: 500));
    }
  }

  Future<void> _pollNativeReady() async {
    while (mounted && !_nativeReady) {
      final ready = await widget.placements.native.isReady;
      if (!mounted) return;
      if (ready) {
        setState(() => _nativeReady = true);
        return;
      }
      await Future.delayed(const Duration(milliseconds: 500));
    }
  }

  Future<void> _pollFeedNativeReady() async {
    while (mounted && !_feedNativeReady) {
      final ready = await widget.placements.feedNative.isReady;
      if (!mounted) return;
      if (ready) {
        setState(() => _feedNativeReady = true);
        return;
      }
      await Future.delayed(const Duration(milliseconds: 500));
    }
  }

  Future<void> _pollSmallNativeReady() async {
    while (mounted && !_smallNativeReady) {
      final ready = await widget.placements.smallNative.isReady;
      if (!mounted) return;
      if (ready) {
        setState(() => _smallNativeReady = true);
        return;
      }
      await Future.delayed(const Duration(milliseconds: 500));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            spacing: 12,
            children: [
              Row(
                children: [
                  const Text('Premium (disable ads)'),
                  Switch(
                    value: _premium,
                    onChanged: (value) async {
                      setState(() => _premium = value);
                      await widget.placements.setPremium(value);
                    },
                  ),
                ],
              ),
              ElevatedButton(
                onPressed: () => widget.placements.globalInterstitial.show(),
                child: const Text('Show Global Interstitial'),
              ),
              ElevatedButton(
                onPressed: () => widget.placements.rewarded.show(
                  onUserEarnedReward: (reward) => setState(() => _lastReward = reward),
                ),
                child: const Text('Show Rewarded Ad'),
              ),
              Text(
                _lastReward == null
                    ? 'Last reward: none yet'
                    : 'Last reward: ${_lastReward!.amount} ${_lastReward!.type}',
              ),
              ElevatedButton(
                onPressed: () => widget.placements.appOpen.show(),
                child: const Text('Show App Open Ad'),
              ),
              // Chỉ hiện lối vào này khi UMP yêu cầu - đây là yêu cầu bắt buộc theo chính sách
              // AdMob/Google Play, không phải tuỳ chọn.
              if (_privacyOptionsRequired)
                ElevatedButton(
                  onPressed: () => AdFlowCore.showPrivacyOptionsForm(),
                  child: const Text('Privacy options'),
                ),
              if (_nativeReady) ...[
                ElevatedButton(
                  onPressed: () async {
                    final result = await widget.placements.native.reload();
                    if (!mounted || !result.success) return;
                    setState(() => _nativeGeneration++);
                  },
                  child: const Text('Reload Native Ad'),
                ),
                AdFlowNativeAdView(
                  key: ValueKey(_nativeGeneration),
                  ad: widget.placements.native,
                ),
              ],
              // Placement Native thứ 2, render bằng renderer tùy biến 'compactCard' (đăng ký ở
              // MainActivity.kt) - minh hoạ nhiều native ad placement cùng lúc, mỗi placement 1 UI
              // riêng (khác hẳn layout dọc mặc định ở trên).
              if (_feedNativeReady)
                AdFlowNativeAdView(
                  ad: widget.placements.feedNative,
                  rendererId: 'compactCard',
                  height: 100,
                ),
              // Placement Native thứ 3, render bằng renderer có sẵn 'small'
              // (DefaultSmallNativeAdRenderer, đăng ký ở MainActivity.kt) - minh hoạ rendererId
              // dùng lại renderer có sẵn trong lib chứ không nhất thiết phải tự viết Kotlin mới.
              if (_smallNativeReady)
                AdFlowNativeAdView(
                  ad: widget.placements.smallNative,
                  rendererId: 'small',
                  height: 80,
                ),
            ],
          ),
        ),
      ),
      // Banner neo cố định ở đáy màn hình (bottomNavigationBar) - Scaffold tự chừa chỗ cho body,
      // nên không đè lên nội dung khác dù chiều cao ad thật lớn hơn giá trị mặc định 1 chút.
      bottomNavigationBar: _bannerReady
          ? SafeArea(child: AdFlowBannerAdView(ad: widget.placements.banner))
          : null,
    );
  }
}
