package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.generated.PAdFlowError
import com.adflow.adflow_flutter.generated.PAdRevenueEvent
import com.adflow.adflow_flutter.generated.PAdState
import com.adflow.adflow_flutter.generated.PAdStateKind
import com.adflow.adflow_flutter.generated.PAdType
import com.adflow.adflow_flutter.generated.PBannerSize
import com.adflow.adflow_flutter.generated.PBlockReason
import com.adflow.adflow_flutter.generated.PConsentStatus
import com.adflow.adflow_flutter.generated.PDebugGeography
import com.adflow.adflow_flutter.generated.PPrivacyOptionsRequirement
import com.adflow.adflow_flutter.generated.PRetryPolicy
import com.adflow.adflow_flutter.generated.PRewardItem
import com.adflow.core.AdFlowError
import com.adflow.core.AdState
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.banner.BannerSize
import com.adflow.core.config.RetryPolicy
import com.adflow.core.consent.ConsentDebugGeography
import com.adflow.core.consent.ConsentStatus
import com.adflow.core.consent.PrivacyOptionsRequirement
import com.adflow.core.revenue.AdRevenueEvent
import com.adflow.core.rewarded.RewardItem

/**
 * Chuyển đổi 2 chiều giữa kiểu Pigeon-sinh (P-prefix) và kiểu thật của adflow-core. Enum
 * Pigeon-sinh (PAdType/PBlockReason/PConsentStatus/PPrivacyOptionsRequirement/PDebugGeography/
 * PBannerSize) trùng tên hệt enum core (SCREAMING_SNAKE_CASE) nên map qua .name/.valueOf được,
 * không cần liệt kê từng case.
 */

internal fun PRetryPolicy.toCore(): RetryPolicy =
    RetryPolicy(
        initialDelayMs = initialDelayMs,
        multiplier = multiplier,
        maxDelayMs = maxDelayMs,
        maxRetries = maxRetries.toInt(),
    )

internal fun PBannerSize.toCore(): BannerSize =
    when (this) {
        PBannerSize.BANNER -> BannerSize.BANNER
        PBannerSize.LARGE_BANNER -> BannerSize.LARGE_BANNER
        PBannerSize.MEDIUM_RECTANGLE -> BannerSize.MEDIUM_RECTANGLE
        PBannerSize.ADAPTIVE -> BannerSize.ADAPTIVE
    }

internal fun PDebugGeography.toCore(): ConsentDebugGeography = ConsentDebugGeography.valueOf(name)

internal fun AdFlowError.toPigeon(): PAdFlowError = PAdFlowError(code = code.toLong(), message = message)

internal fun BlockReason.toPigeon(): PBlockReason = PBlockReason.valueOf(name)

internal fun AdType.toPigeon(): PAdType = PAdType.valueOf(name)

internal fun ConsentStatus.toPigeon(): PConsentStatus = PConsentStatus.valueOf(name)

internal fun PrivacyOptionsRequirement.toPigeon(): PPrivacyOptionsRequirement =
    PPrivacyOptionsRequirement.valueOf(name)

internal fun RewardItem.toPigeon(): PRewardItem = PRewardItem(type = type, amount = amount.toLong())

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

internal fun AdState.toPigeon(): PAdState =
    when (this) {
        is AdState.Idle -> PAdState(kind = PAdStateKind.IDLE)
        is AdState.Loading -> PAdState(kind = PAdStateKind.LOADING)
        is AdState.Loaded -> PAdState(kind = PAdStateKind.LOADED, loadedAtMs = loadedAtMs)
        is AdState.Failed -> PAdState(
            kind = PAdStateKind.FAILED,
            error = error.toPigeon(),
            willRetry = willRetry,
            nextRetryDelayMs = nextRetryDelayMs,
        )
        is AdState.Showing -> PAdState(kind = PAdStateKind.SHOWING)
    }
