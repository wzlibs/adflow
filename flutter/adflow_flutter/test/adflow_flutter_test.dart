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
}
