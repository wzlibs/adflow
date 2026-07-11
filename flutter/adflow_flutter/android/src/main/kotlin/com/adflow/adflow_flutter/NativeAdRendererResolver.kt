package com.adflow.adflow_flutter

import android.util.Log
import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.core.nativead.NativeAdRenderer

private const val TAG = "AdFlowFlutter"

/**
 * Tra `rendererId` (khai trong DSL lúc `AdFlowHostApi.initialize()`, hoặc truyền riêng cho từng
 * `AdFlowNative` widget qua platform view) sang renderer Kotlin đã đăng ký qua
 * [AdflowFlutterPlugin.registerNativeAdRenderer]. `rendererId` null hoặc không khớp renderer nào
 * đã đăng ký -> fallback [DefaultMediumNativeAdRenderer] (không bao giờ crash/collapse vì thiếu
 * đăng ký).
 */
internal fun resolveRenderer(rendererId: String?, renderers: Map<String, NativeAdRenderer>): NativeAdRenderer {
    if (rendererId == null) return DefaultMediumNativeAdRenderer()
    return renderers[rendererId] ?: run {
        Log.w(
            TAG,
            "Không tìm thấy NativeAdRenderer đã đăng ký cho rendererId='$rendererId' - dùng " +
                "renderer mặc định. Kiểm tra đã gọi AdflowFlutterPlugin.registerNativeAdRenderer() " +
                "trong MainActivity.configureFlutterEngine() trước khi dùng rendererId này chưa.",
        )
        DefaultMediumNativeAdRenderer()
    }
}
