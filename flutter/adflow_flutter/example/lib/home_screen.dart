import 'package:adflow_flutter/adflow_flutter.dart';
import 'package:flutter/material.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _premium = false;
  RewardItem? _lastReward;
  bool _privacyOptionsRequired = false;

  @override
  void initState() {
    super.initState();
    _loadPrivacyRequirement();
  }

  Future<void> _loadPrivacyRequirement() async {
    final requirement = await AdFlow.getPrivacyOptionsRequirement();
    if (mounted) {
      setState(
        () => _privacyOptionsRequired =
            requirement == PrivacyOptionsRequirement.required,
      );
    }
  }

  Widget _loading(BuildContext context, double height) => SizedBox(
    height: height,
    child: const Center(child: CircularProgressIndicator()),
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Premium'),
              value: _premium,
              onChanged: (value) {
                setState(() => _premium = value);
                final enabled = !value;
                AdFlow.interstitial('splash_interstitial').setEnabled(enabled);
                AdFlow.interstitial('global_interstitial').setEnabled(enabled);
                AdFlow.appOpen('app_open').setEnabled(enabled);
                AdFlow.banner('home_banner').setEnabled(enabled);
                AdFlow.native('home_native').setEnabled(enabled);
                AdFlow.native('feed_native').setEnabled(enabled);
                AdFlow.native('small_native').setEnabled(enabled);
                // rewarded cố tình không tắt - user chủ động xem để nhận thưởng.
              },
            ),
            FilledButton(
              onPressed: () =>
                  AdFlow.interstitial('global_interstitial').show(),
              child: const Text('Show interstitial'),
            ),
            FilledButton(
              onPressed: () => AdFlow.rewarded('rewarded').show(
                onUserEarnedReward: (reward) =>
                    setState(() => _lastReward = reward),
              ),
              child: const Text('Show rewarded'),
            ),
            Text(
              _lastReward == null
                  ? 'Last reward: none'
                  : 'Last reward: ${_lastReward!.amount} ${_lastReward!.type}',
            ),
            FilledButton(
              onPressed: () => AdFlow.appOpen('app_open').show(),
              child: const Text('Show app open'),
            ),
            if (_privacyOptionsRequired)
              TextButton(
                onPressed: AdFlow.showPrivacyOptionsForm,
                child: const Text('Privacy options'),
              ),
            TextButton.icon(
              onPressed: () => AdFlow.native('home_native').reload(),
              icon: const Icon(Icons.refresh),
              label: const Text('Reload native'),
            ),
            AdFlowNative(
              'home_native',
              loading: (context) => _loading(context, 250),
            ),
            AdFlowNative(
              'feed_native',
              height: 100,
              rendererId: 'compactCard',
              loading: (context) => _loading(context, 100),
            ),
            AdFlowNative(
              'small_native',
              height: 120,
              rendererId: 'small',
              loading: (context) => _loading(context, 120),
            ),
          ],
        ),
      ),
    ),
    bottomNavigationBar: SafeArea(
      child: AdFlowBanner(
        'home_banner',
        loading: (context) => _loading(context, 50),
      ),
    ),
  );
}
