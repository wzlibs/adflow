package com.adflow.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Tự động show [appOpen] mỗi khi app quay lại foreground, theo đúng pattern App Open Ads mà Google
 * khuyến nghị - miễn là ad đã ready và không có full-screen ad nào khác
 * ([AdFlowCore.isShowingFullScreenAd]) đang hiển thị, vì show App Open ad đè lên
 * Interstitial/Rewarded đang hiển thị sẽ phá vỡ UX (và vi phạm policy của AdMob).
 *
 * Gọi [start] một lần, ví dụ từ `Application.onCreate()`. Dùng [ProcessLifecycleOwner] (không phải
 * lifecycle theo từng Activity) để phát hiện đúng một lần chuyển sang foreground ở cấp độ app -
 * chuyển giữa 2 Activity trong cùng app không được kích hoạt việc này, chỉ khi app vào background
 * rồi quay lại mới được.
 */
class AppOpenAdController(
    private val application: Application,
    private val appOpen: AppOpenAdManager,
) {
    private var currentActivity: Activity? = null
    private var pendingForegroundShow: Boolean = false
    private var started: Boolean = false

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            currentActivity = activity
            if (pendingForegroundShow) {
                pendingForegroundShow = false
                showIfPossible()
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (currentActivity === activity) currentActivity = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            onForegroundStart()
        }

        override fun onStop(owner: LifecycleOwner) {
            onForegroundStop()
        }
    }

    /** Idempotent - gọi lần 2 khi đã start rồi thì không làm gì, nên caller không cần tự canh
     * chừng việc gọi [start] nhiều hơn 1 lần (ví dụ từ `Application.onCreate` bị gọi lại). */
    fun start() {
        if (started) return
        started = true
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
    }

    /** Đảo ngược [start]; không làm gì nếu chưa start. */
    fun stop() {
        if (!started) return
        started = false
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
    }

    /**
     * Process start có thể xảy ra trước khi Android gọi callback resume của Activity tương ứng,
     * nên một lần chuyển sang foreground mà chưa có [currentActivity] không được bỏ qua - nó được
     * trì hoãn (defer) đến khi [onActivityResumed] chạy, thay vì bị mất.
     */
    internal fun onForegroundStart() {
        if (currentActivity == null) {
            pendingForegroundShow = true
            return
        }
        showIfPossible()
    }

    internal fun onForegroundStop() {
        pendingForegroundShow = false
    }

    /**
     * Quyết định show-hoặc-bỏ-qua thực sự, được tách riêng khỏi phần lifecycle plumbing kích hoạt
     * nó, để có thể gọi trực tiếp trong test mà không cần tạo ra cả một lần chuyển process
     * lifecycle đầy đủ.
     */
    internal fun showIfPossible() {
        val activity = currentActivity ?: return
        if (AdFlowCore.isShowingFullScreenAd) return
        if (!appOpen.isReady()) return
        appOpen.show(activity, ShowCallback.NONE)
    }
}
