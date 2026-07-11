import 'dart:async';

import 'package:adflow_flutter/adflow_flutter.dart';
import 'package:flutter/material.dart';

import 'home_screen.dart';
import 'splash_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AdFlow.initialize(
    placements: const [
      InterstitialPlacement(
        'splash_interstitial',
        adUnits: ['ca-app-pub-3940256099942544/1033173712'],
        preload: false,
      ),
      InterstitialPlacement(
        'global_interstitial',
        adUnits: ['ca-app-pub-3940256099942544/1033173712'],
      ),
      AppOpenPlacement(
        'app_open',
        adUnits: ['ca-app-pub-3940256099942544/9257395921'],
        autoShowOnForeground: true,
      ),
      RewardedPlacement(
        'rewarded',
        adUnits: ['ca-app-pub-3940256099942544/5224354917'],
      ),
      BannerPlacement(
        'home_banner',
        adUnits: ['ca-app-pub-3940256099942544/6300978111'],
        size: BannerSize.banner,
      ),
      NativePlacement(
        'home_native',
        adUnits: ['ca-app-pub-3940256099942544/2247696110'],
      ),
      NativePlacement(
        'feed_native',
        adUnits: ['ca-app-pub-3940256099942544/2247696110'],
        rendererId: 'compactCard',
      ),
      NativePlacement(
        'small_native',
        adUnits: ['ca-app-pub-3940256099942544/2247696110'],
        rendererId: 'small',
      ),
    ],
  );
  unawaited(AdFlow.requestConsentIfNeeded());
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});
  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  bool _showSplash = true;

  @override
  Widget build(BuildContext context) => MaterialApp(
    home: _showSplash
        ? SplashScreen(onDone: () => setState(() => _showSplash = false))
        : const HomeScreen(),
  );
}
