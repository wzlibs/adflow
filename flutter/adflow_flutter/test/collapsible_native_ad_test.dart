import 'package:adflow_flutter/adflow_flutter.dart';
import 'package:adflow_flutter/src/ad_flow_dispatcher.dart';
import 'package:adflow_flutter/src/generated/adflow_api.g.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('AdFlowCollapsibleNative renders native loading slot initially', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: AdFlowCollapsibleNative(
          nativePlacementId: 'collapsible_loading_native',
          bannerPlacementId: 'collapsible_loading_banner',
          nativeLoading: (_) => const Text('native loading'),
        ),
      ),
    );
    expect(find.text('native loading'), findsOneWidget);
  });

  testWidgets(
    'AdFlowCollapsibleNative shows close button once native is loaded, tapping it collapses to banner',
    (tester) async {
      const nativeId = 'collapsible_tap_native';
      const bannerId = 'collapsible_tap_banner';
      final dispatcher = AdFlowDispatcher.instance;
      AdFlowCollapseReason? collapsedReason;

      await tester.pumpWidget(
        MaterialApp(
          home: AdFlowCollapsibleNative(
            nativePlacementId: nativeId,
            bannerPlacementId: bannerId,
            nativeLoading: (_) => const Text('native loading'),
            bannerLoading: (_) => const Text('banner loading'),
            onCollapse: (reason) => collapsedReason = reason,
          ),
        ),
      );
      expect(find.text('native loading'), findsOneWidget);

      dispatcher.onAdState(
        nativeId,
        PAdState(kind: PAdStateKind.loaded, loadedAtMs: 1),
      );
      await tester.pump();
      expect(find.text('×'), findsOneWidget);

      await tester.tap(find.text('×'));
      await tester.pump();

      expect(collapsedReason, AdFlowCollapseReason.userClosed);
      expect(find.text('banner loading'), findsOneWidget);
      expect(find.text('×'), findsNothing);
    },
  );

  testWidgets(
    'AdFlowCollapsibleNative auto-collapses to banner when native fails to load',
    (tester) async {
      const nativeId = 'collapsible_fail_native';
      const bannerId = 'collapsible_fail_banner';
      final dispatcher = AdFlowDispatcher.instance;
      AdFlowCollapseReason? collapsedReason;
      AdFlowError? nativeError;

      await tester.pumpWidget(
        MaterialApp(
          home: AdFlowCollapsibleNative(
            nativePlacementId: nativeId,
            bannerPlacementId: bannerId,
            nativeFailed: (_, error) => const Text('native failed'),
            bannerLoading: (_) => const Text('banner loading'),
            onNativeError: (error) => nativeError = error,
            onCollapse: (reason) => collapsedReason = reason,
          ),
        ),
      );

      dispatcher.onAdState(
        nativeId,
        PAdState(
          kind: PAdStateKind.failed,
          error: PAdFlowError(code: 3, message: 'no fill'),
          willRetry: false,
        ),
      );
      await tester.pump(); // flush AdStateEffects' post-frame onError callback
      await tester.pump(); // flush the setState(_collapsed = true) it triggers

      expect(nativeError?.message, 'no fill');
      expect(collapsedReason, AdFlowCollapseReason.nativeUnavailable);
      expect(find.text('banner loading'), findsOneWidget);
      expect(find.text('native failed'), findsNothing);
    },
  );

  testWidgets(
    'AdFlowCollapsibleNative resets to native when nativePlacementId changes after collapse',
    (tester) async {
      const nativeId = 'collapsible_reset_native';
      const bannerId = 'collapsible_reset_banner';
      const otherNativeId = 'collapsible_reset_native_2';
      final dispatcher = AdFlowDispatcher.instance;

      await tester.pumpWidget(
        MaterialApp(
          home: AdFlowCollapsibleNative(
            nativePlacementId: nativeId,
            bannerPlacementId: bannerId,
            nativeLoading: (_) => const Text('native loading'),
          ),
        ),
      );

      dispatcher.onAdState(
        nativeId,
        PAdState(kind: PAdStateKind.loaded, loadedAtMs: 1),
      );
      await tester.pump();
      await tester.tap(find.text('×'));
      await tester.pump();
      expect(find.text('×'), findsNothing);

      await tester.pumpWidget(
        MaterialApp(
          home: AdFlowCollapsibleNative(
            nativePlacementId: otherNativeId,
            bannerPlacementId: bannerId,
            nativeLoading: (_) => const Text('native loading'),
          ),
        ),
      );
      await tester.pump();

      expect(find.text('native loading'), findsOneWidget);
    },
  );
}
