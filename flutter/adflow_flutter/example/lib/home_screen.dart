import 'package:adflow_flutter/adflow_flutter.dart';
import 'package:flutter/material.dart';

import 'ad_placements.dart';

/// Mirror Dart của `HomeScreen.kt` - gọi thẳng [AdFlowBannerAdView]/[AdFlowNativeAdView], không
/// check `isReady` trước. Nếu bị chặn (chưa `load()` xong hoặc `showRule` từ chối),
/// `onShowBlocked` bật cờ `*Blocked` tương ứng để ẩn widget đó; 1 vòng poll định kỳ tự bump
/// `*Generation` (đổi `Key`) để thử tạo lại platform view khi đang bị chặn, cho tới khi thành
/// công - xem `_retryWhileBlocked`.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.placements});

  final AdPlacements placements;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _premium = false;
  PRewardItem? _lastReward;
  bool _bannerBlocked = false;
  bool _nativeBlocked = false;
  bool _feedNativeBlocked = false;
  bool _smallNativeBlocked = false;
  bool _privacyOptionsRequired = false;
  // Đổi Key của widget tương ứng để ép Flutter huỷ-tạo lại platform view đó - dùng cả khi
  // reload() thành công (đọc đúng native ad mới nhất) lẫn khi retry sau khi bị chặn.
  int _bannerGeneration = 0;
  int _nativeGeneration = 0;
  int _feedNativeGeneration = 0;
  int _smallNativeGeneration = 0;

  @override
  void initState() {
    super.initState();
    _retryWhileBlocked(
      isBlocked: () => _bannerBlocked,
      retry: () => setState(() {
        _bannerBlocked = false;
        _bannerGeneration++;
      }),
    );
    _retryWhileBlocked(
      isBlocked: () => _nativeBlocked,
      retry: () => setState(() {
        _nativeBlocked = false;
        _nativeGeneration++;
      }),
    );
    _retryWhileBlocked(
      isBlocked: () => _feedNativeBlocked,
      retry: () => setState(() {
        _feedNativeBlocked = false;
        _feedNativeGeneration++;
      }),
    );
    _retryWhileBlocked(
      isBlocked: () => _smallNativeBlocked,
      retry: () => setState(() {
        _smallNativeBlocked = false;
        _smallNativeGeneration++;
      }),
    );
    _loadPrivacyOptionsRequirement();
  }

  Future<void> _loadPrivacyOptionsRequirement() async {
    final requirement = await AdFlowCore.getPrivacyOptionsRequirement();
    if (!mounted) return;
    setState(() => _privacyOptionsRequired = requirement == PPrivacyOptionsRequirement.required);
  }

  /// Poll vĩnh viễn (mỗi 500ms) trong khi [isBlocked] còn true - mỗi lần vẫn còn bị chặn thì gọi
  /// [retry] (reset cờ blocked + bump generation) để widget tương ứng được build lại và tự thử
  /// tạo platform view lần nữa.
  Future<void> _retryWhileBlocked({
    required bool Function() isBlocked,
    required VoidCallback retry,
  }) async {
    while (mounted) {
      await Future.delayed(const Duration(milliseconds: 500));
      if (!mounted) return;
      if (isBlocked()) retry();
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
              if (!_nativeBlocked)
                ElevatedButton(
                  onPressed: () async {
                    final result = await widget.placements.native.reload();
                    if (!mounted || !result.success) return;
                    setState(() => _nativeGeneration++);
                  },
                  child: const Text('Reload Native Ad'),
                ),
              // Gọi thẳng, không check isReady() trước - onShowBlocked ẩn widget này đi (bọc
              // bằng "if (!_nativeBlocked)") và _retryWhileBlocked tự thử lại định kỳ.
              if (!_nativeBlocked)
                AdFlowNativeAdView(
                  key: ValueKey(_nativeGeneration),
                  ad: widget.placements.native,
                  onShowBlocked: (reason) {
                    setState(() => _nativeBlocked = true);
                    debugPrint('Native ad blocked: $reason');
                  },
                ),
              // Placement Native thứ 2, render bằng renderer tùy biến 'compactCard' (đăng ký ở
              // MainActivity.kt) - minh hoạ nhiều native ad placement cùng lúc, mỗi placement 1 UI
              // riêng (khác hẳn layout dọc mặc định ở trên).
              if (!_feedNativeBlocked)
                AdFlowNativeAdView(
                  key: ValueKey(_feedNativeGeneration),
                  ad: widget.placements.feedNative,
                  rendererId: 'compactCard',
                  height: 100,
                  onShowBlocked: (reason) => setState(() => _feedNativeBlocked = true),
                ),
              // Placement Native thứ 3, render bằng renderer có sẵn 'small'
              // (DefaultSmallNativeAdRenderer, đăng ký ở MainActivity.kt) - minh hoạ rendererId
              // dùng lại renderer có sẵn trong lib chứ không nhất thiết phải tự viết Kotlin mới.
              if (!_smallNativeBlocked)
                AdFlowNativeAdView(
                  key: ValueKey(_smallNativeGeneration),
                  ad: widget.placements.smallNative,
                  rendererId: 'small',
                  height: 120,
                  onShowBlocked: (reason) => setState(() => _smallNativeBlocked = true),
                ),
            ],
          ),
        ),
      ),
      // Banner neo cố định ở đáy màn hình (bottomNavigationBar) - Scaffold tự chừa chỗ cho body,
      // nên không đè lên nội dung khác dù chiều cao ad thật lớn hơn giá trị mặc định 1 chút.
      bottomNavigationBar: _bannerBlocked
          ? null
          : SafeArea(
              child: AdFlowBannerAdView(
                key: ValueKey(_bannerGeneration),
                ad: widget.placements.banner,
                onShowBlocked: (reason) {
                  setState(() => _bannerBlocked = true);
                  debugPrint('Banner ad blocked: $reason');
                },
              ),
            ),
    );
  }
}
