package com.adflow.core.fullscreen

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.adflow.core.AdState
import com.adflow.core.engine.AdFlowRuntime

/**
 * Tự động show 1 [AppOpenAd] mỗi khi app quay lại foreground - kích hoạt bởi facade `AdFlow` cho
 * placement bật `autoShowOnForeground = true` trong DSL, theo đúng pattern App Open Ads mà Google
 * khuyến nghị. Không bao giờ show đè lên full-screen ad khác đang hiển thị
 * ([AdFlowRuntime.fullScreenSlot]) - class này chỉ quyết định LÚC NÀO gọi `show()`, mọi
 * check show-interval/showRule khác vẫn do [AppOpenAd.show] tự áp dụng như bình thường.
 *
 * Dùng [ProcessLifecycleOwner] (không phải lifecycle theo từng Activity) để phát hiện đúng 1 lần
 * chuyển sang foreground ở cấp độ app - chuyển giữa 2 Activity trong cùng app không kích hoạt
 * việc này, chỉ khi app vào background rồi quay lại mới được.
 */
internal class AppOpenForegroundObserver(
    private val application: Application,
    private val appOpen: AppOpenAd,
    private val runtime: AdFlowRuntime,
) {
    private var currentActivity: Activity? = null
    private var pendingForegroundShow = false
    private var started = false

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

    /** Idempotent - gọi lần 2 khi đã start rồi thì không làm gì. */
    fun start() {
        if (started) return
        started = true
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
    }

    fun stop() {
        if (!started) return
        started = false
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
    }

    /** Process start có thể xảy ra trước khi Android gọi callback resume của Activity tương ứng,
     * nên 1 lần chuyển sang foreground mà chưa có [currentActivity] không bị bỏ qua - nó được trì
     * hoãn tới khi [onActivityResumed] chạy. */
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

    /** Tách khỏi phần lifecycle plumbing kích hoạt nó để gọi trực tiếp được trong test. */
    internal fun showIfPossible() {
        val activity = currentActivity ?: return
        if (runtime.fullScreenSlot.isShowing) return
        if (appOpen.state.value !is AdState.Loaded) return
        appOpen.show(activity, FullScreenCallback.EMPTY)
    }
}
