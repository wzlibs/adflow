import 'package:adflow_flutter/adflow_flutter.dart';
import 'package:adflow_flutter/src/ad_flow_dispatcher.dart';
import 'package:adflow_flutter/src/generated/adflow_api.g.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('dispatcher routes native states to one stable listenable', () {
    final dispatcher = AdFlowDispatcher.instance;
    final state = dispatcher.stateOf('routing_test');
    dispatcher.onAdState(
      'routing_test',
      PAdState(kind: PAdStateKind.loaded, loadedAtMs: 42),
    );
    expect(dispatcher.stateOf('routing_test'), same(state));
    expect(state.value, isA<AdLoaded>());
    expect((state.value as AdLoaded).loadedAtMs, 42);
  });

  test('awaitTerminalAdState completes when loading fails', () async {
    final dispatcher = AdFlowDispatcher.instance;
    final state = dispatcher.stateOf('await_test');
    final future = awaitTerminalAdState(state, const Duration(seconds: 1));
    dispatcher.onAdState(
      'await_test',
      PAdState(
        kind: PAdStateKind.failed,
        error: PAdFlowError(code: 3, message: 'no fill'),
        willRetry: false,
      ),
    );
    expect(await future, isA<AdFailed>());
  });

  testWidgets('AdFlowBanner switches from loading to failed slot', (
    tester,
  ) async {
    const id = 'banner_widget_test';
    final dispatcher = AdFlowDispatcher.instance;
    await tester.pumpWidget(
      MaterialApp(
        home: AdFlowBanner(
          id,
          loading: (_) => const Text('loading slot'),
          failed: (_, error) => Text('failed: ${error.message}'),
        ),
      ),
    );
    expect(find.text('loading slot'), findsOneWidget);
    dispatcher.onAdState(
      id,
      PAdState(
        kind: PAdStateKind.failed,
        error: PAdFlowError(code: 3, message: 'no fill'),
        willRetry: false,
      ),
    );
    await tester.pump();
    expect(find.text('failed: no fill'), findsOneWidget);
    expect(find.text('loading slot'), findsNothing);
  });

  testWidgets('AdFlowNative renders its loading slot while idle', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: AdFlowNative(
          'native_widget_test',
          loading: (_) => const Text('native loading'),
        ),
      ),
    );
    expect(find.text('native loading'), findsOneWidget);
  });

  testWidgets(
    'AdFlowNative onLoading/onLoaded/onError fire once per phase, and setState inside them is safe',
    (tester) async {
      const id = 'native_effects_test';
      final dispatcher = AdFlowDispatcher.instance;
      var loadingCount = 0;
      var loadedCount = 0;
      var errorCount = 0;
      AdFlowError? lastError;
      var setStateRuns = 0;

      await tester.pumpWidget(
        MaterialApp(
          home: StatefulBuilder(
            builder: (context, setState) => AdFlowNative(
              id,
              onLoading: () => setState(() {
                loadingCount++;
                setStateRuns++;
              }),
              onLoaded: () => setState(() {
                loadedCount++;
                setStateRuns++;
              }),
              onError: (error) => setState(() {
                errorCount++;
                lastError = error;
                setStateRuns++;
              }),
            ),
          ),
        ),
      );
      await tester.pump(); // flush post-frame callback từ initState (state ban đầu = Idle)
      expect(loadingCount, 1); // Idle tính vào nhóm "loading"

      dispatcher.onAdState(id, PAdState(kind: PAdStateKind.loading));
      await tester.pump();
      expect(loadingCount, 1); // vẫn cùng nhóm loading - không bắn lại

      dispatcher.onAdState(
        id,
        PAdState(kind: PAdStateKind.loaded, loadedAtMs: 1),
      );
      await tester.pump();
      expect(loadedCount, 1);

      dispatcher.onAdState(
        id,
        PAdState(
          kind: PAdStateKind.failed,
          error: PAdFlowError(code: 1, message: 'boom'),
          willRetry: false,
        ),
      );
      await tester.pump();
      expect(errorCount, 1);
      expect(lastError?.message, 'boom');

      expect(setStateRuns, 3); // chứng minh setState() chạy 3 lần mà không throw
    },
  );

  testWidgets('AdFlowBanner onLoading/onLoaded fire as state changes', (
    tester,
  ) async {
    const id = 'banner_effects_test';
    final dispatcher = AdFlowDispatcher.instance;
    var loadingCount = 0;
    var loadedCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) => AdFlowBanner(
            id,
            onLoading: () => setState(() => loadingCount++),
            onLoaded: () => setState(() => loadedCount++),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(loadingCount, 1);

    dispatcher.onAdState(id, PAdState(kind: PAdStateKind.loaded, loadedAtMs: 1));
    await tester.pump();
    expect(loadedCount, 1);
  });
}
