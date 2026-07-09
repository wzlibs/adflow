package com.adflow.adflow_flutter_example

import com.adflow.admob.nativead.DefaultSmallNativeAdRenderer
import com.adflow.adflow_flutter.AdflowFlutterPlugin
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        AdflowFlutterPlugin.registerNativeAdRenderer(
            flutterEngine,
            "compactCard",
            CompactCardNativeAdRenderer(),
        )
        // 'small' không phải renderer tự viết - DefaultSmallNativeAdRenderer có sẵn trong
        // adflow-admob (chỉ headline + body, không media/cta). rendererId hoạt động y hệt dù
        // renderer đến từ lib hay app tự viết.
        AdflowFlutterPlugin.registerNativeAdRenderer(
            flutterEngine,
            "small",
            DefaultSmallNativeAdRenderer(),
        )
    }
}
