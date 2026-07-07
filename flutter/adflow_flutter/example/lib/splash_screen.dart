import 'package:flutter/material.dart';

import 'ad_placements.dart';

const _pollInterval = Duration(milliseconds: 500);
const _readyTimeout = Duration(seconds: 5);

/// Mirror Dart của `SplashScreen.kt` - `AdPlacements` đã kích hoạt `splashInterstitial.load()` bất
/// đồng bộ từ `main()`. Poll `isReady` trước khi `show()`, có timeout để user không bao giờ bị kẹt
/// ở splash nếu ad không bao giờ ready (no fill, không network...).
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key, required this.placements, required this.onDone});

  final AdPlacements placements;
  final VoidCallback onDone;

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _showSplashInterstitial();
  }

  Future<void> _showSplashInterstitial() async {
    var waited = Duration.zero;
    while (!await widget.placements.splashInterstitial.isReady && waited < _readyTimeout) {
      await Future.delayed(_pollInterval);
      waited += _pollInterval;
    }
    if (!mounted) return;

    if (!await widget.placements.splashInterstitial.isReady) {
      widget.onDone();
      return;
    }

    await widget.placements.splashInterstitial.show();
    if (!mounted) return;
    widget.onDone();
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('AdFlow demo'),
          ],
        ),
      ),
    );
  }
}
