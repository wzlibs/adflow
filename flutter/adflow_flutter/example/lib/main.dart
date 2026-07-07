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
  // Không chờ load() xong mới runApp() - load ads chạy song song ở background, giống
  // AdFlowDemoApp.onCreate() bản Kotlin gốc.
  unawaited(placements.loadAll());
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
