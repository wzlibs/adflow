import 'dart:async';

import 'package:flutter/widgets.dart';

import 'ad_state.dart';
import 'banner_ad.dart';
import 'native_ad.dart';
import 'types.dart';

/// Lý do 1 [AdFlowCollapsibleNative] chuyển từ native sang banner - truyền vào [AdFlowCollapsibleNative.onCollapse].
enum AdFlowCollapseReason {
  /// User tự bấm nút đóng.
  userClosed,

  /// Native load thất bại (hoặc bị block) - tự chuyển sang banner thay vì bỏ trống ô quảng cáo.
  nativeUnavailable,
}

/// Native ad có nút đóng: hiển thị native trước, chuyển sang banner ([bannerPlacementId]) khi user
/// bấm nút đóng hoặc khi native load thất bại. Compose thẳng [AdFlowNative]/[AdFlowBanner] đã có -
/// không tự dựng lại AndroidView/state wiring.
///
/// Sau khi native [AdLoaded] được [bannerPreloadDelay], banner được load ngầm (không đưa vào widget
/// tree) để khi user bấm đóng, banner đã sẵn trong cache và hiện ra ngay, không phải chờ loading.
class AdFlowCollapsibleNative extends StatefulWidget {
  const AdFlowCollapsibleNative({
    required this.nativePlacementId,
    required this.bannerPlacementId,
    super.key,
    this.nativeHeight = 250,
    this.bannerHeight = 50,
    this.rendererId,
    this.bannerPreloadDelay = const Duration(seconds: 2),
    this.collapseIcon,
    this.collapseIconAlignment = AlignmentDirectional.topEnd,
    this.nativeLoading,
    this.nativeFailed,
    this.bannerLoading,
    this.bannerFailed,
    this.onNativeLoading,
    this.onNativeLoaded,
    this.onNativeError,
    this.onBannerLoading,
    this.onBannerLoaded,
    this.onBannerError,
    this.onCollapse,
  });

  final String nativePlacementId;
  final String bannerPlacementId;
  final double nativeHeight;
  final double bannerHeight;
  final String? rendererId;

  /// Thời gian chờ sau khi native loaded trước khi âm thầm load banner ngoài widget tree.
  final Duration bannerPreloadDelay;

  /// Icon cho nút đóng - null dùng icon mặc định (vòng tròn + dấu ×). Đặt trong 1 vùng bấm 44x44
  /// logic pixel bất kể kích thước icon, để đảm bảo tap target đủ lớn.
  final Widget? collapseIcon;

  /// Vị trí nút đóng đè lên native ad.
  final AlignmentGeometry collapseIconAlignment;

  final WidgetBuilder? nativeLoading;
  final Widget Function(BuildContext context, AdFlowError error)? nativeFailed;
  final WidgetBuilder? bannerLoading;
  final Widget Function(BuildContext context, AdFlowError error)? bannerFailed;

  final VoidCallback? onNativeLoading;
  final VoidCallback? onNativeLoaded;
  final void Function(AdFlowError error)? onNativeError;
  final VoidCallback? onBannerLoading;
  final VoidCallback? onBannerLoaded;
  final void Function(AdFlowError error)? onBannerError;

  /// Bắn đúng 1 lần khi widget chuyển từ native sang banner - xem [AdFlowCollapseReason] cho lý do.
  final void Function(AdFlowCollapseReason reason)? onCollapse;

  @override
  State<AdFlowCollapsibleNative> createState() => _AdFlowCollapsibleNativeState();
}

class _AdFlowCollapsibleNativeState extends State<AdFlowCollapsibleNative> {
  late AdFlowNativeAd _nativeObserver;
  late AdFlowBannerAd _bannerPreload;
  Timer? _preloadTimer;
  bool _collapsed = false;

  @override
  void initState() {
    super.initState();
    _nativeObserver = AdFlowNativeAd(widget.nativePlacementId);
    _bannerPreload = AdFlowBannerAd(widget.bannerPlacementId);
  }

  @override
  void didUpdateWidget(AdFlowCollapsibleNative oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.nativePlacementId != widget.nativePlacementId) {
      _nativeObserver = AdFlowNativeAd(widget.nativePlacementId);
      _preloadTimer?.cancel();
      _preloadTimer = null;
      _collapsed = false;
    }
    if (oldWidget.bannerPlacementId != widget.bannerPlacementId) {
      _bannerPreload = AdFlowBannerAd(widget.bannerPlacementId);
      _preloadTimer?.cancel();
      _preloadTimer = null;
      _collapsed = false;
    }
  }

  @override
  void dispose() {
    _preloadTimer?.cancel();
    super.dispose();
  }

  void _scheduleBannerPreload() {
    _preloadTimer?.cancel();
    _preloadTimer = Timer(widget.bannerPreloadDelay, () {
      _preloadTimer = null;
      if (mounted) unawaited(_bannerPreload.load());
    });
  }

  void _collapse(AdFlowCollapseReason reason) {
    if (_collapsed) return;
    _preloadTimer?.cancel();
    _preloadTimer = null;
    // Đảm bảo có load ngay nếu user bấm đóng (hoặc native fail) trước khi timer preload kịp chạy -
    // load() là engine.ensureLoaded(), no-op nếu ad đã sẵn sàng/đang load.
    unawaited(_bannerPreload.load());
    setState(() => _collapsed = true);
    widget.onCollapse?.call(reason);
  }

  @override
  Widget build(BuildContext context) {
    if (_collapsed) {
      return AdFlowBanner(
        widget.bannerPlacementId,
        height: widget.bannerHeight,
        loading: widget.bannerLoading,
        failed: widget.bannerFailed,
        onLoading: widget.onBannerLoading,
        onLoaded: widget.onBannerLoaded,
        onError: widget.onBannerError,
      );
    }

    final native = AdFlowNative(
      widget.nativePlacementId,
      height: widget.nativeHeight,
      rendererId: widget.rendererId,
      loading: widget.nativeLoading,
      failed: widget.nativeFailed,
      onLoading: widget.onNativeLoading,
      onLoaded: () {
        _scheduleBannerPreload();
        widget.onNativeLoaded?.call();
      },
      onError: (error) {
        widget.onNativeError?.call(error);
        _collapse(AdFlowCollapseReason.nativeUnavailable);
      },
    );

    return ValueListenableBuilder<AdState>(
      valueListenable: _nativeObserver.state,
      builder: (context, state, child) {
        if (state is! AdLoaded) return child!;
        return Stack(
          clipBehavior: Clip.none,
          children: [
            child!,
            Positioned.fill(
              child: Align(
                alignment: widget.collapseIconAlignment,
                child: Padding(
                  padding: const EdgeInsets.all(8),
                  child: GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTap: () => _collapse(AdFlowCollapseReason.userClosed),
                    child: SizedBox(
                      width: 44,
                      height: 44,
                      child: Center(
                        child: widget.collapseIcon ?? const _DefaultCollapseIcon(),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        );
      },
      child: native,
    );
  }
}

class _DefaultCollapseIcon extends StatelessWidget {
  const _DefaultCollapseIcon();

  @override
  Widget build(BuildContext context) => Container(
    width: 24,
    height: 24,
    alignment: Alignment.center,
    decoration: const BoxDecoration(color: Color(0x99000000), shape: BoxShape.circle),
    child: const Text(
      '×',
      style: TextStyle(
        color: Color(0xFFFFFFFF),
        fontSize: 16,
        height: 1,
        fontWeight: FontWeight.bold,
      ),
    ),
  );
}
