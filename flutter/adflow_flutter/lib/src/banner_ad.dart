import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'config.dart';
import 'flutter_api_dispatcher.dart';
import 'generated/adflow_api.g.dart';

const _bannerViewType = 'adflow/banner_ad_view';

/// Facade Dart cho 1 placement Banner. Không có `show()`/`getView()` như bản Kotlin thuần - dùng
/// [AdFlowBannerAdView] (PlatformView) để render sau khi [load] hoàn tất.
class AdFlowBannerAd {
  AdFlowBannerAd(PlacementConfig config) : placementId = config.placementId {
    _hostApi.create(config.toPigeon());
  }

  final String placementId;
  static final BannerAdHostApi _hostApi = BannerAdHostApi();

  Future<bool> get isReady => _hostApi.isReady(placementId);

  Future<PLoadResult> load() => _hostApi.load(placementId);

  Future<void> setEnabled(bool enabled) => _hostApi.setEnabled(placementId, enabled);
}

/// Render [ad] qua `PlatformView` Android thật (`BannerAdManager.getView()`). Luôn an toàn để
/// build ngay, không cần `await ad.load()` hay check `isReady` trước - phía Kotlin không throw,
/// chỉ render 1 View rỗng và báo lý do qua [onShowBlocked] khi bị chặn. Nhưng platform view chỉ
/// được tạo đúng 1 lần lúc build - nếu bị chặn ngay từ đầu, nó sẽ kẹt ở trạng thái rỗng mãi trừ
/// khi được build lại với 1 [Key] mới. Pattern khuyến nghị: dùng [onShowBlocked] để đặt cờ "đang
/// bị chặn" (ẩn widget này đi), rồi poll định kỳ để bump 1 giá trị dùng làm [Key] khi còn đang bị
/// chặn, ép build lại và tự thử lần nữa cho tới khi thành công - xem
/// `example/lib/home_screen.dart` để có ví dụ đầy đủ.
class AdFlowBannerAdView extends StatefulWidget {
  const AdFlowBannerAdView({super.key, required this.ad, this.height = 50, this.onShowBlocked});

  final AdFlowBannerAd ad;
  final double height;

  /// Gọi lại khi banner không hiển thị được (chưa `load()` xong - [PBlockReason.notReady], hoặc
  /// `showRule` đang từ chối - [PBlockReason.ruleRejected]). Tuỳ chọn - bỏ qua nếu không cần biết
  /// lý do.
  final void Function(PBlockReason reason)? onShowBlocked;

  @override
  State<AdFlowBannerAdView> createState() => _AdFlowBannerAdViewState();
}

class _AdFlowBannerAdViewState extends State<AdFlowBannerAdView> {
  @override
  void initState() {
    super.initState();
    FlutterApiDispatcher.instance.registerShowEventHandler(widget.ad.placementId, (
      kind,
      error,
      blockReason,
      reward,
    ) {
      if (kind == PShowEventKind.showBlocked && blockReason != null) {
        widget.onShowBlocked?.call(blockReason);
      }
    });
  }

  @override
  void dispose() {
    FlutterApiDispatcher.instance.unregisterShowEventHandler(widget.ad.placementId);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: widget.height,
      child: AndroidView(
        viewType: _bannerViewType,
        creationParams: {'placementId': widget.ad.placementId},
        creationParamsCodec: const StandardMessageCodec(),
      ),
    );
  }
}
