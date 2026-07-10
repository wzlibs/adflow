package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.generated.PAdFlowError
import com.adflow.adflow_flutter.generated.PAdRevenueEvent
import com.adflow.adflow_flutter.generated.PAdType
import com.adflow.adflow_flutter.generated.PBlockReason
import com.adflow.adflow_flutter.generated.PConsentStatus
import com.adflow.adflow_flutter.generated.PLoadResult
import com.adflow.adflow_flutter.generated.PPlacementConfig
import com.adflow.adflow_flutter.generated.PPrivacyOptionsRequirement
import com.adflow.adflow_flutter.generated.PRewardItem
import com.adflow.adflow_flutter.generated.PRetryPolicy
import com.adflow.adflow_flutter.generated.PShowIntervalConfig
import com.adflow.core.AdFlowError
import com.adflow.core.AdLoadResult
import com.adflow.core.revenue.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.consent.ConsentStatus
import com.adflow.core.config.PlacementConfig
import com.adflow.core.consent.PrivacyOptionsRequirement
import com.adflow.core.config.RetryPolicy
import com.adflow.core.rewarded.RewardItem
import com.adflow.core.config.ShowIntervalConfig

/**
 * Chuyển đổi 2 chiều giữa kiểu Pigeon-sinh (P-prefix) và kiểu thật của adflow-core. Enum
 * Pigeon-sinh (PAdType/PBlockReason) trùng tên hệt AdType/BlockReason (UPPER_SNAKE_CASE) nên map
 * qua .name/.valueOf được, không cần liệt kê từng case.
 */

internal fun PRetryPolicy.toCore(): RetryPolicy =
    RetryPolicy(
        initialDelayMs = initialDelayMs,
        multiplier = multiplier,
        maxDelayMs = maxDelayMs,
        maxRetries = maxRetries.toInt(),
    )

internal fun PPlacementConfig.toCore(): PlacementConfig =
    PlacementConfig(
        placementId = placementId,
        enabled = enabled,
        preloadEnabled = preloadEnabled,
        adUnitIds = adUnitIds,
        retryPolicy = retryPolicy.toCore(),
        // loadRule/showRule luôn null - AdRule không bridge qua channel được, xem README.
        loadRule = null,
        showRule = null,
        expiryMs = expiryMs,
    )

internal fun PShowIntervalConfig.toCore(): ShowIntervalConfig =
    ShowIntervalConfig(
        interstitialAfterInterstitialMs = interstitialAfterInterstitialMs,
        appOpenAfterAppOpenMs = appOpenAfterAppOpenMs,
        interstitialAfterAppOpenMs = interstitialAfterAppOpenMs,
        appOpenAfterInterstitialMs = appOpenAfterInterstitialMs,
    )

internal fun AdFlowError.toPigeon(): PAdFlowError = PAdFlowError(code = code.toLong(), message = message)

internal fun BlockReason.toPigeon(): PBlockReason = PBlockReason.valueOf(name)

internal fun AdType.toPigeon(): PAdType = PAdType.valueOf(name)

internal fun ConsentStatus.toPigeon(): PConsentStatus = PConsentStatus.valueOf(name)

internal fun PrivacyOptionsRequirement.toPigeon(): PPrivacyOptionsRequirement =
    PPrivacyOptionsRequirement.valueOf(name)

internal fun RewardItem.toPigeon(): PRewardItem = PRewardItem(type = type, amount = amount.toLong())

internal fun AdLoadResult.toPigeon(): PLoadResult =
    when (this) {
        is AdLoadResult.Success -> PLoadResult(success = true, error = null)
        is AdLoadResult.Failure -> PLoadResult(success = false, error = error.toPigeon())
    }

internal fun AdRevenueEvent.toPigeon(): PAdRevenueEvent =
    PAdRevenueEvent(
        placementId = placementId,
        adType = adType.toPigeon(),
        adUnitId = adUnitId,
        valueMicros = valueMicros,
        currencyCode = currencyCode,
        precision = precision,
        adNetwork = adNetwork,
    )
