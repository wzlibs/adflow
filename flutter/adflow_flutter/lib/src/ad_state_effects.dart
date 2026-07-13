import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import 'ad_state.dart';
import 'types.dart';

enum _Phase { loading, loaded, error }

_Phase _phaseOf(AdState state) => switch (state) {
  AdFailed() => _Phase.error,
  AdLoaded() => _Phase.loaded,
  _ => _Phase.loading, // AdIdle, AdLoading (AdShowing không áp dụng cho Native/Banner)
};

/// Dựng UI theo [AdState] (qua [builder], giống hệt `ValueListenableBuilder`) và đồng thời bắn
/// [onLoading]/[onLoaded]/[onError] mỗi khi state đổi NHÓM (loading/loaded/error - không phân biệt
/// AdIdle với AdLoading, khớp đúng cách [builder] cũng chỉ phân biệt 2 nhóm này). Dùng chung cho
/// `AdFlowNative`/`AdFlowBanner`.
///
/// [onLoading]/[onLoaded]/[onError] luôn chạy sau khi frame hiện tại build xong
/// ([WidgetsBinding.addPostFrameCallback]), kể cả lần gọi đầu tiên lúc mount - để app gọi
/// `setState()` bên trong các callback này luôn an toàn, không rơi vào lỗi "setState() or
/// markNeedsBuild() called during build" như khi cố làm side-effect ngay trong 1 WidgetBuilder.
class AdStateEffects extends StatefulWidget {
  const AdStateEffects({
    super.key,
    required this.state,
    required this.builder,
    this.onLoading,
    this.onLoaded,
    this.onError,
  });

  final ValueListenable<AdState> state;
  final Widget Function(BuildContext context, AdState state) builder;
  final VoidCallback? onLoading;
  final VoidCallback? onLoaded;
  final void Function(AdFlowError error)? onError;

  @override
  State<AdStateEffects> createState() => _AdStateEffectsState();
}

class _AdStateEffectsState extends State<AdStateEffects> {
  _Phase? _lastNotifiedPhase;

  @override
  void initState() {
    super.initState();
    widget.state.addListener(_handleChange);
    _handleChange(); // báo ngay trạng thái hiện tại lúc mount, vẫn qua post-frame để an toàn
  }

  @override
  void didUpdateWidget(AdStateEffects oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (identical(oldWidget.state, widget.state)) return;
    oldWidget.state.removeListener(_handleChange);
    widget.state.addListener(_handleChange);
    _lastNotifiedPhase = null;
    _handleChange();
  }

  @override
  void dispose() {
    widget.state.removeListener(_handleChange);
    super.dispose();
  }

  void _handleChange() {
    final state = widget.state.value;
    final phase = _phaseOf(state);
    if (phase == _lastNotifiedPhase) return;
    _lastNotifiedPhase = phase;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      switch (state) {
        case AdFailed(:final error):
          widget.onError?.call(error);
        case AdLoaded():
          widget.onLoaded?.call();
        default:
          widget.onLoading?.call();
      }
    });
  }

  @override
  Widget build(BuildContext context) => ValueListenableBuilder<AdState>(
    valueListenable: widget.state,
    builder: (context, state, _) => widget.builder(context, state),
  );
}
