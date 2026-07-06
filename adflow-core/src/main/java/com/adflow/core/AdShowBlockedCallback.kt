package com.adflow.core

/**
 * The one show()-outcome signal shared by every ad type's show callback ([ShowCallback],
 * [RewardedAdCallback]), regardless of how differently the rest of their contracts read. Letting
 * both extend this lets [CachedAdLoaderBase] report the not-ready/showRule-rejected blocks once,
 * generically, instead of each concrete manager duplicating that reporting itself.
 */
interface AdShowBlockedCallback {
    fun onShowBlocked(reason: BlockReason) {}
}
