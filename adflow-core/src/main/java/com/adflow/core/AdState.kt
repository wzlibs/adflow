package com.adflow.core

/**
 * Trạng thái vòng đời của 1 placement, expose qua `StateFlow<AdState>` trên mọi controller/view -
 * client quan sát để hiện shimmer ([Loading]), hiện ad ([Loaded]) hay ẩn slot ([Failed]).
 */
sealed interface AdState {
    /** Đã đăng ký nhưng chưa có lượt load nào chạy (hoặc placement đang bị chặn bởi gate). */
    data object Idle : AdState

    /** Một lượt load (có thể gồm nhiều chu kỳ waterfall + retry) đang chạy. */
    data object Loading : AdState

    /** Đã có ad sẵn sàng trong cache. */
    data class Loaded(val loadedAtMs: Long) : AdState

    /**
     * Một chu kỳ waterfall vừa thất bại (toàn bộ ad unit no-fill).
     *
     * [willRetry] = true: engine sẽ tự thử lại sau [nextRetryDelayMs] - client có thể dismiss
     * slot ngay nhưng vẫn nên sẵn sàng nhận [Loaded] muộn. [willRetry] = false: lượt load này đã
     * dùng hết [com.adflow.core.config.RetryPolicy.maxRetries] chu kỳ và dừng thật sự - sẽ không
     * có thêm request nền nào cho tới khi có nhu cầu mới (view attach lại, show(), load()/reload(),
     * app quay lại foreground với placement preload).
     */
    data class Failed(
        val error: AdFlowError,
        val willRetry: Boolean,
        val nextRetryDelayMs: Long?,
    ) : AdState

    /** Ad đang hiển thị trên màn hình (chỉ dùng cho các loại full-screen). */
    data object Showing : AdState
}

val AdState.isLoaded: Boolean get() = this is AdState.Loaded
