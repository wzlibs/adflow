package com.adflow.core

/**
 * Tín hiệu kết quả show() duy nhất được chia sẻ bởi callback show() của mọi loại ad
 * ([ShowCallback], [RewardedAdCallback]), dù phần còn lại trong contract của chúng khác nhau thế
 * nào. Cho cả hai cùng extend interface này giúp [CachedAdLoaderBase] báo cáo việc bị chặn do
 * not-ready/showRule-rejected một cách chung, tổng quát, thay vì mỗi manager cụ thể phải tự lặp
 * lại logic báo cáo đó.
 */
interface AdShowBlockedCallback {
    fun onShowBlocked(reason: BlockReason) {}
}
