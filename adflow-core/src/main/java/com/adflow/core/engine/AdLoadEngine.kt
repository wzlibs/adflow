package com.adflow.core.engine

import com.adflow.core.AdFlowError
import com.adflow.core.AdListener
import com.adflow.core.AdState
import com.adflow.core.BlockReason
import com.adflow.core.config.PlacementConfig
import com.adflow.core.logging.AdFlowEvent
import com.adflow.core.network.AdLoadException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Máy trạng thái load/cache/retry/expiry dùng chung cho mọi loại ad (Interstitial/App
 * Open/Rewarded/Native/Banner) - thay 3 tầng kế thừa `Simple/Expiring/CachedAdLoaderBase` +
 * `RetryingAdLoader` + `WaterfallLoader` của v1 bằng 1 class generic duy nhất (composition thay
 * kế thừa), với state là output hạng nhất thay vì chỉ callback 1 lần.
 *
 * Toàn bộ mutation (state, cachedAd, loadJob) được giả định chạy trên 1 dispatcher đơn luồng
 * (`Dispatchers.Main.immediate` lúc chạy thật, `TestDispatcher` lúc test) - không cần khóa đồng bộ
 * riêng, khớp với cách mọi controller/view gọi vào engine từ main thread.
 *
 * [loadOne] ném [AdLoadException] khi 1 ad unit no-fill/lỗi - engine bắt để rơi xuống ad unit kế
 * tiếp trong waterfall của [PlacementConfig.adUnitIds]. [onDrop] được gọi trên ad CŨ khi nó bị
 * thay bởi ad mới (qua [forceReload] thành công) hoặc bị bỏ vì hết hạn - chỗ adapter giải phóng
 * tài nguyên (vd `NativeAd.destroy()`).
 */
internal class AdLoadEngine<T : Any>(
    private val config: PlacementConfig,
    private val loadOne: suspend (adUnitId: String) -> T,
    private val onDrop: (T) -> Unit,
    private val runtime: AdFlowRuntime,
    private val scope: CoroutineScope,
) {
    private val adType get() = config.adType
    private val logger get() = runtime.logger

    val state: StateFlow<AdState> get() = _state
    private val _state = MutableStateFlow<AdState>(AdState.Idle)

    private var cachedAd: T? = null
    private var loadedAtMs: Long = 0L
    private var loadJob: Job? = null
    private val listeners = mutableSetOf<AdListener>()

    /** true nếu có ad cache và (không có expiry hoặc chưa quá hạn). */
    val isReady: Boolean
        get() {
            dropIfExpired()
            return cachedAd != null
        }

    fun addListener(listener: AdListener) {
        listeners += listener
        replay(listener)
    }

    fun removeListener(listener: AdListener) {
        listeners -= listener
    }

    private fun replay(listener: AdListener) {
        when (val s = _state.value) {
            AdState.Loading -> listener.onAdLoading()
            is AdState.Loaded -> listener.onAdLoaded()
            is AdState.Failed -> listener.onAdFailedToLoad(s.error, s.willRetry)
            else -> Unit
        }
    }

    private fun setState(newState: AdState) {
        _state.value = newState
        when (newState) {
            AdState.Loading -> listeners.forEach { it.onAdLoading() }
            is AdState.Loaded -> listeners.forEach { it.onAdLoaded() }
            is AdState.Failed -> listeners.forEach { it.onAdFailedToLoad(newState.error, newState.willRetry) }
            else -> Unit
        }
    }

    /** Chuyển state sang [AdState.Showing] - gọi bởi full-screen controller ngay sau [take]
     * thành công, trước khi thực sự hiển thị. */
    fun markShowing() {
        setState(AdState.Showing)
    }

    /** Đưa state về [AdState.Idle] - gọi bởi full-screen controller sau khi ad bị đóng và
     * placement không preload lại. */
    fun markIdle() {
        setState(AdState.Idle)
    }

    private fun passesGates(): BlockReason? = when {
        !runtime.consentAllowsAdRequests -> BlockReason.CONSENT_REQUIRED
        config.loadRule?.isAllowed(config.placementId) == false -> BlockReason.RULE_REJECTED
        else -> null
    }

    private fun reportBlocked(reason: BlockReason) {
        logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, reason.name)
        listeners.forEach { it.onAdBlocked(reason) }
    }

    /**
     * Mở 1 lượt load nếu chưa có ad sẵn sàng và không có lượt nào đang chạy - coalesce: gọi
     * trùng lúc đang chạy chỉ no-op (không mở lượt độc lập thứ 2). Đây là điểm demand-driven mở
     * lại bộ đếm retry: gọi lại sau khi 1 lượt trước đã kết thúc `Failed(willRetry=false)` sẽ bắt
     * đầu 1 lượt mới, đếm retry lại từ 0.
     */
    fun ensureLoaded() {
        if (isReady) return
        if (loadJob?.isActive == true) return
        val blocked = passesGates()
        if (blocked != null) {
            reportBlocked(blocked)
            return
        }
        loadJob = scope.launch { runCycle(forcing = false) }
    }

    /**
     * Ép fetch ad MỚI dù ad đang cache vẫn còn hạn. Ad cũ (nếu có) giữ nguyên - state vẫn ở
     * [AdState.Loaded] cũ - cho tới khi ad mới load thành công, lúc đó state phát [AdState.Loaded]
     * mới (timestamp mới) để view tự rebind, rồi [onDrop] chạy trên ad cũ. Nếu thất bại (kể cả
     * hết retry), âm thầm giữ nguyên ad cũ + state cũ - không báo lỗi qua listener, vì từ góc nhìn
     * client không có gì thay đổi.
     */
    fun forceReload() {
        if (loadJob?.isActive == true) return
        val blocked = passesGates()
        if (blocked != null) {
            reportBlocked(blocked)
            return
        }
        loadJob = scope.launch { runCycle(forcing = true) }
    }

    /** Đọc cache không tiêu thụ - dùng cho Native/Banner (có thể gắn vào nhiều view). */
    fun peek(): T? {
        dropIfExpired()
        return cachedAd
    }

    /** Lấy ra và xóa cache - dùng cho full-screen (ad chỉ show 1 lần). */
    fun take(): T? {
        dropIfExpired()
        val ad = cachedAd ?: return null
        cachedAd = null
        return ad
    }

    private fun dropIfExpired() {
        val expiry = config.expiryMs ?: return
        val ad = cachedAd ?: return
        if (runtime.clock() - loadedAtMs >= expiry) {
            cachedAd = null
            logger.log(config.placementId, adType, AdFlowEvent.EXPIRED, null)
            onDrop(ad)
            setState(AdState.Idle)
        }
    }

    /**
     * Hẹn giờ tự bỏ ad khi hết hạn, KHÔNG đợi có ai gọi [isReady]/[peek]/[take] - để `state`
     * không bao giờ "nói dối" [AdState.Loaded] cho 1 ad thực ra đã hết hạn. An toàn nếu ad đã bị
     * thay trước khi job này chạy: [dropIfExpired] tính lại theo [loadedAtMs] hiện tại (đã là của
     * ad mới), nên job cũ chạy trễ chỉ là no-op.
     */
    private fun scheduleExpiry(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            dropIfExpired()
        }
    }

    private suspend fun runCycle(forcing: Boolean) {
        if (!forcing) setState(AdState.Loading)
        logger.log(config.placementId, adType, AdFlowEvent.LOADING, null)

        var attempt = 0
        while (true) {
            attempt += 1
            val result = runWaterfall()
            if (result != null) {
                val previous = cachedAd
                cachedAd = result
                loadedAtMs = runtime.clock()
                logger.log(config.placementId, adType, AdFlowEvent.LOADED, null)
                // Luôn phát Loaded mới, kể cả khi forcing - đây là tín hiệu để view tự rebind
                // sang ad mới (xem NativeAdController.reload()).
                setState(AdState.Loaded(loadedAtMs))
                if (previous != null && previous !== result) onDrop(previous)
                config.expiryMs?.let { scheduleExpiry(it) }
                return
            }

            logger.log(config.placementId, adType, AdFlowEvent.NO_FILL, null)
            if (attempt >= config.retryPolicy.maxRetries) {
                if (!forcing) {
                    val error = AdFlowError(AdFlowError.Codes.NO_FILL, "waterfall exhausted after $attempt attempt(s)")
                    setState(AdState.Failed(error, willRetry = false, nextRetryDelayMs = null))
                }
                // forcing: âm thầm bỏ cuộc, ad cũ + state cũ giữ nguyên.
                return
            }

            val delayMs = config.retryPolicy.delayForAttempt(attempt)
            logger.log(config.placementId, adType, AdFlowEvent.RETRYING, "attempt=$attempt delay=$delayMs")
            if (!forcing) {
                val error = AdFlowError(AdFlowError.Codes.NO_FILL, "no fill, retrying")
                setState(AdState.Failed(error, willRetry = true, nextRetryDelayMs = delayMs))
            }
            delay(delayMs)
            if (!forcing) setState(AdState.Loading)
        }
    }

    private suspend fun runWaterfall(): T? {
        for (adUnitId in config.adUnitIds) {
            logger.log(config.placementId, adType, AdFlowEvent.WATERFALL_NEXT, adUnitId)
            try {
                return loadOne(adUnitId)
            } catch (e: AdLoadException) {
                continue
            }
        }
        return null
    }
}
