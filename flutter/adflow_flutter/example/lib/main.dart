import 'dart:async';

import 'package:adflow_flutter/adflow_flutter.dart';
import 'package:flutter/material.dart';

import 'ad_placements.dart';
import 'home_screen.dart';
import 'splash_screen.dart';

late final AdPlacements placements;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AdFlowCore.initialize();
  placements = AdPlacements();
  await placements.appOpen.enableAutoShowOnForeground();
  // Không chờ resolve consent/load() xong mới runApp() - chạy song song ở background, giống
  // MainActivity.kt bản Kotlin gốc. load() tự động tôn trọng consent (xem README), nên thứ tự
  // "xin consent rồi mới load" ở đây chỉ để tránh lãng phí 1 request, không phải điều kiện bắt buộc.
  unawaited(AdFlowCore.requestConsentIfNeeded().then((_) => placements.loadAll()));
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
  Widget build(BuildContext context) {
    return MaterialApp(
      home: _showSplash
          ? SplashScreen(
              placements: placements,
              onDone: () => setState(() => _showSplash = false),
            )
          : HomeScreen(placements: placements),
    );
  }
}
