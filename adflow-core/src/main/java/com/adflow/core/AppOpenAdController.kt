package com.adflow.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Shows [appOpen] automatically whenever the app returns to the foreground, following Google's
 * recommended App Open Ads pattern - as long as it's ready and no other full-screen ad
 * ([AdFlowCore.isShowingFullScreenAd]) is already on screen, since showing an App Open ad on top
 * of an already-visible Interstitial/Rewarded would be broken UX (and against AdMob policy).
 *
 * Call [start] once, e.g. from `Application.onCreate()`. Uses [ProcessLifecycleOwner] (not
 * per-Activity lifecycle) to detect a true app-level foreground transition - switching between two
 * Activities within the app must not trigger this, only backgrounding and returning should.
 */
class AppOpenAdController(
    private val application: Application,
    private val appOpen: AppOpenAdManager,
) {
    private var currentActivity: Activity? = null

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            currentActivity = activity
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
            showIfPossible()
        }
    }

    fun start() {
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
    }

    /**
     * The actual show-or-skip decision, kept separate from the lifecycle plumbing that triggers
     * it so it can be exercised directly without driving a full process lifecycle transition.
     */
    internal fun showIfPossible() {
        val activity = currentActivity ?: return
        if (AdFlowCore.isShowingFullScreenAd) return
        if (!appOpen.isReady()) return
        appOpen.show(activity, ShowCallback.NONE)
    }
}
