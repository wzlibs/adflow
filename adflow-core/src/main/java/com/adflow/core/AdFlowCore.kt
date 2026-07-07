package com.adflow.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

object AdFlowCore {
    var logger: AdFlowLogger = LogcatAdFlowLogger()
        private set

    private val revenueLoggers = mutableListOf<RevenueLogger>()

    private var foregroundAction: (() -> Unit)? = null
    private var foregroundActionRan = false

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            onForegroundStart()
        }
    }

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

    /**
     * Đăng ký [action] để chạy đúng 1 lần, vào thời điểm app thực sự vào foreground lần đầu tiên
     * trong vòng đời process (dùng [ProcessLifecycleOwner], không phải lifecycle của riêng 1
     * Activity) - ví dụ để init provider và load ads. Gọi 1 lần từ `Application.onCreate()`.
     *
     * Lý do cần gate qua đây thay vì load thẳng trong `Application.onCreate()`: onCreate() chạy
     * cho MỌI lý do OS khởi động process, kể cả khi chỉ để chạy 1 BroadcastReceiver/Service ở
     * background (ví dụ xử lý FCM push) mà không hề có Activity nào sắp hiển thị. Load ads ngay
     * trong onCreate() sẽ lãng phí ad request/network/pin cho những lần process bị đánh thức như
     * vậy, vì ads được load ra sẽ không bao giờ có cơ hội hiển thị.
     *
     * Nếu app đã ở foreground từ trước khi gọi hàm này, [action] chạy ngay lập tức (đồng bộ) vì
     * [androidx.lifecycle.Lifecycle] tự phát lại state hiện tại cho observer mới đăng ký.
     *
     * Gọi lần 2 trở đi là no-op - chỉ [action] của lần gọi đầu tiên được đăng ký/chạy.
     */
    fun runOnFirstForeground(action: () -> Unit) {
        if (foregroundActionRan || foregroundAction != null) return
        foregroundAction = action
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
    }

    /**
     * Handler thực sự đứng sau [foregroundObserver], tách riêng khỏi phần lifecycle plumbing kích
     * hoạt nó để có thể gọi trực tiếp trong test mà không cần tạo ra cả một lần chuyển process
     * lifecycle đầy đủ (cùng lý do với [AppOpenAdController.onForegroundStart]).
     */
    internal fun onForegroundStart() {
        val action = foregroundAction ?: return
        if (foregroundActionRan) return
        foregroundActionRan = true
        foregroundAction = null
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foregroundObserver)
        action()
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
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foregroundObserver)
        foregroundAction = null
        foregroundActionRan = false
    }
}
