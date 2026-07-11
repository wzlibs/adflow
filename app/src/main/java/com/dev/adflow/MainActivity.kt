package com.dev.adflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.adflow.core.AdFlow
import com.dev.adflow.ui.theme.AdFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // GDPR/UMP: xin consent trước khi load ads thật sự - không bắt buộc gọi ở đây cụ thể, chỉ
        // cần 1 Activity nào tiện. Mọi lượt load() tự động tôn trọng consent, nên các placement đã
        // preload trước đó (qua AdFlow.initialize's runOnFirstForeground) vô hại nếu consent chưa
        // resolve xong - chỉ fail an toàn.
        AdFlow.consent.requestIfNeeded(this) {}

        setContent {
            AdFlowTheme {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    SplashScreen(onDone = { showSplash = false })
                } else {
                    HomeScreen()
                }
            }
        }
    }
}
