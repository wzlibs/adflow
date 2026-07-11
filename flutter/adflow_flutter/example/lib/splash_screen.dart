import 'package:adflow_flutter/adflow_flutter.dart';
import 'package:flutter/material.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key, required this.onDone});
  final VoidCallback onDone;

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _showAd();
  }

  Future<void> _showAd() async {
    final ad = AdFlow.interstitial('splash_interstitial');
    final state = await ad.awaitReady(const Duration(seconds: 8));
    if (!mounted) return;
    if (state is AdLoaded) await ad.show();
    if (mounted) widget.onDone();
  }

  @override
  Widget build(BuildContext context) =>
      const Scaffold(body: Center(child: CircularProgressIndicator()));
}
