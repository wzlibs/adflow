package com.adflow.adflow_flutter.callbacks

import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PRewardItem
import com.adflow.adflow_flutter.generated.PShowEventKind
import com.adflow.core.rewarded.RewardItem
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RewardedAdCallbackBridgeTest {

    @Test
    fun `onUserEarnedReward forwards USER_EARNED_REWARD with the mapped reward`() {
        val flutterApi = mock<AdFlowFlutterApi>()
        val bridge = RewardedAdCallbackBridge("p1", flutterApi)

        bridge.onUserEarnedReward(RewardItem(type = "coins", amount = 5))

        verify(flutterApi).onShowEvent(
            eq("p1"),
            eq(PShowEventKind.USER_EARNED_REWARD),
            eq(null),
            eq(null),
            argThat<PRewardItem> { type == "coins" && amount == 5L },
            any(),
        )
    }

    @Test
    fun `onAdDismissed forwards DISMISSED like ShowCallbackBridge`() {
        val flutterApi = mock<AdFlowFlutterApi>()
        val bridge = RewardedAdCallbackBridge("p1", flutterApi)

        bridge.onAdDismissed()

        verify(flutterApi).onShowEvent(eq("p1"), eq(PShowEventKind.DISMISSED), eq(null), eq(null), eq(null), any())
    }
}
