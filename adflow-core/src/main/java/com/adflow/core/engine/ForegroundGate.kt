package com.adflow.core.engine

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Chạy 1 action đúng 1 lần vào lần app THẬT SỰ vào foreground đầu tiên trong vòng đời process
 * (dùng [ProcessLifecycleOwner], không phải lifecycle riêng 1 Activity) - tránh lãng phí ad
 * request khi process chỉ bị OS đánh thức để làm việc nền (FCM push...) mà không có Activity nào
 * sắp hiển thị. Nếu app đã ở foreground từ trước khi gọi, action chạy ngay lập tức (đồng bộ) vì
 * Lifecycle tự phát lại state hiện tại cho observer mới đăng ký.
 */
internal class ForegroundGate {
    private var action: (() -> Unit)? = null
    private var ran = false

    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            onForegroundStart()
        }
    }

    /** Gọi lần 2 trở đi là no-op - chỉ action của lần gọi đầu tiên được đăng ký/chạy. */
    fun runOnFirstForeground(action: () -> Unit) {
        if (ran || this.action != null) return
        this.action = action
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    /** Tách khỏi phần lifecycle plumbing kích hoạt nó để gọi trực tiếp được trong test. */
    internal fun onForegroundStart() {
        val pending = action ?: return
        if (ran) return
        ran = true
        action = null
        ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        pending()
    }
}
