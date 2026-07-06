package com.adflow.core

object AdFlowCore {
    var logger: AdFlowLogger = LogcatAdFlowLogger()
        private set

    private val revenueLoggers = mutableListOf<RevenueLogger>()

    /**
     * True trong khi có bất kỳ full-screen ad nào (Interstitial/AppOpen/Rewarded) đang hiển thị
     * trên màn hình. Được claim/release một cách atomic qua [tryClaimFullScreenSlot]/
     * [releaseFullScreenSlot] bởi mọi luồng show() full-screen, để 2 full-screen ad - dù từ 2
     * manager khác nhau, hay gọi show() đồng thời - không thể nào cùng hiển thị 1 lúc. Được
     * [AppOpenAdController] tham chiếu như một bước kiểm tra sơ bộ (best-effort) để không bao giờ
     * thử show App Open ad đè lên một full-screen ad đang hiển thị.
     */
    var isShowingFullScreenAd: Boolean = false
        private set

    /**
     * Chiếm quyền (claim) slot full-screen duy nhất một cách atomic: trả về true và đánh dấu đã
     * chiếm nếu slot đang trống, hoặc trả về false (không side-effect) nếu đã có full-screen ad
     * khác đang hiển thị. Mỗi lần claim thành công phải đi kèm đúng 1 lần gọi
     * [releaseFullScreenSlot] khi vòng đời show() của ad đó thực sự kết thúc (dismissed, show lỗi,
     * hoặc SDK throw đồng bộ) - caller không được tiếp tục hiển thị ad sau khi nhận kết quả false.
     */
    @Synchronized
    fun tryClaimFullScreenSlot(): Boolean {
        if (isShowingFullScreenAd) return false
        isShowingFullScreenAd = true
        return true
    }

    /** Giải phóng slot đã chiếm trước đó qua [tryClaimFullScreenSlot]. */
    @Synchronized
    fun releaseFullScreenSlot() {
        isShowingFullScreenAd = false
    }

    fun configure(
        showIntervalConfig: ShowIntervalConfig = ShowIntervalConfig(),
        logger: AdFlowLogger = LogcatAdFlowLogger(),
    ) {
        this.logger = logger
        AdShowIntervalPolicy.configure(showIntervalConfig)
    }

    fun addRevenueLogger(logger: RevenueLogger) {
        revenueLoggers += logger
    }

    fun removeRevenueLogger(logger: RevenueLogger) {
        revenueLoggers -= logger
    }

    fun dispatchRevenue(event: AdRevenueEvent) {
        revenueLoggers.forEach { it.onRevenuePaid(event) }
    }

    internal fun reset() {
        logger = LogcatAdFlowLogger()
        revenueLoggers.clear()
        isShowingFullScreenAd = false
        AdShowIntervalPolicy.reset()
    }
}
