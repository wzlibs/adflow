import 'package:adflow_flutter/src/flutter_api_dispatcher.dart';
import 'package:adflow_flutter/src/generated/adflow_api.g.dart';
import 'package:adflow_flutter/src/show_event_support.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('FlutterApiDispatcher', () {
    test('routes onShowEvent to the handler registered for that placementId only', () {
      final dispatcher = FlutterApiDispatcher.instance;
      final received = <String>[];

      dispatcher.registerShowEventHandler('p1', (kind, error, blockReason, reward) {
        received.add('p1:${kind.name}');
      });
      dispatcher.registerShowEventHandler('p2', (kind, error, blockReason, reward) {
        received.add('p2:${kind.name}');
      });

      dispatcher.onShowEvent('p1', PShowEventKind.shown, null, null, null);
      dispatcher.onShowEvent('p2', PShowEventKind.dismissed, null, null, null);

      expect(received, ['p1:shown', 'p2:dismissed']);

      dispatcher.unregisterShowEventHandler('p1');
      dispatcher.unregisterShowEventHandler('p2');
    });

    test('does not call a handler after it has been unregistered', () {
      final dispatcher = FlutterApiDispatcher.instance;
      var callCount = 0;
      dispatcher.registerShowEventHandler('p1', (kind, error, blockReason, reward) {
        callCount++;
      });

      dispatcher.unregisterShowEventHandler('p1');
      dispatcher.onShowEvent('p1', PShowEventKind.shown, null, null, null);

      expect(callCount, 0);
    });

    test('forwards onRevenuePaid to every registered listener', () {
      final dispatcher = FlutterApiDispatcher.instance;
      final received = <PAdRevenueEvent>[];
      dispatcher.addRevenueListener(received.add);

      final event = PAdRevenueEvent(
        placementId: 'p1',
        adType: PAdType.interstitial,
        adUnitId: 'unit-1',
        valueMicros: 1000000,
        currencyCode: 'USD',
        precision: 'ESTIMATED',
      );
      dispatcher.onRevenuePaid(event);

      expect(received, [event]);
    });
  });

  group('showEventFuture', () {
    test('completes on dismissed and calls onAdDismissed', () async {
      var dismissedCalled = false;
      final future = showEventFuture(
        'p1',
        onAdDismissed: () => dismissedCalled = true,
        invokeShow: (placementId) {
          FlutterApiDispatcher.instance.onShowEvent(placementId, PShowEventKind.dismissed, null, null, null);
        },
      );

      await future;

      expect(dismissedCalled, isTrue);
    });

    test('shown does not complete the future - only a terminal event does', () async {
      var shownCalled = false;
      var completed = false;
      final future = showEventFuture(
        'p1',
        onAdShown: () => shownCalled = true,
        invokeShow: (placementId) {
          FlutterApiDispatcher.instance.onShowEvent(placementId, PShowEventKind.shown, null, null, null);
        },
      );
      future.then((_) => completed = true);

      await Future<void>.delayed(Duration.zero);

      expect(shownCalled, isTrue);
      expect(completed, isFalse);

      // Kết thúc show thật sự để không rò rỉ handler đã đăng ký sang test khác.
      FlutterApiDispatcher.instance.onShowEvent('p1', PShowEventKind.dismissed, null, null, null);
      await future;
    });

    test('completes on failedToShow and forwards the error', () async {
      PAdFlowError? receivedError;
      final future = showEventFuture(
        'p1',
        onAdFailedToShow: (error) => receivedError = error,
        invokeShow: (placementId) {
          FlutterApiDispatcher.instance.onShowEvent(
            placementId,
            PShowEventKind.failedToShow,
            PAdFlowError(code: 1, message: 'no fill'),
            null,
            null,
          );
        },
      );

      await future;

      expect(receivedError?.code, 1);
      expect(receivedError?.message, 'no fill');
    });

    test('completes on showBlocked and forwards the reason', () async {
      PBlockReason? receivedReason;
      final future = showEventFuture(
        'p1',
        onShowBlocked: (reason) => receivedReason = reason,
        invokeShow: (placementId) {
          FlutterApiDispatcher.instance.onShowEvent(
            placementId,
            PShowEventKind.showBlocked,
            null,
            PBlockReason.disabled,
            null,
          );
        },
      );

      await future;

      expect(receivedReason, PBlockReason.disabled);
    });

    test('userEarnedReward does not complete the future but forwards the reward', () async {
      PRewardItem? receivedReward;
      var completed = false;
      final future = showEventFuture(
        'p1',
        onUserEarnedReward: (reward) => receivedReward = reward,
        invokeShow: (placementId) {
          FlutterApiDispatcher.instance.onShowEvent(
            placementId,
            PShowEventKind.userEarnedReward,
            null,
            null,
            PRewardItem(type: 'coins', amount: 5),
          );
        },
      );
      future.then((_) => completed = true);

      await Future<void>.delayed(Duration.zero);

      expect(receivedReward?.type, 'coins');
      expect(completed, isFalse);

      FlutterApiDispatcher.instance.onShowEvent('p1', PShowEventKind.dismissed, null, null, null);
      await future;
    });
  });
}
