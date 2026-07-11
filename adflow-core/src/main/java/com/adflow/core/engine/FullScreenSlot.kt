package com.adflow.core.engine

/**
 * Slot độc quyền full-screen: 2 full-screen ad (dù khác placement, hay show() đồng thời từ 2
 * thread) không bao giờ cùng hiển thị. Mỗi lần [tryClaim] thành công phải đi kèm đúng 1 lần
 * [release] khi vòng đời hiển thị thật sự kết thúc (dismissed, show lỗi, hoặc SDK throw đồng bộ).
 */
internal class FullScreenSlot {
    var isShowing: Boolean = false
        private set

    @Synchronized
    fun tryClaim(): Boolean {
        if (isShowing) return false
        isShowing = true
        return true
    }

    @Synchronized
    fun release() {
        isShowing = false
    }
}
