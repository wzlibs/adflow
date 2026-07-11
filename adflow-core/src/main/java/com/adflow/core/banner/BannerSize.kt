package com.adflow.core.banner

sealed interface BannerSize {
    /** 320x50. */
    data object BANNER : BannerSize

    /** 320x100. */
    data object LARGE_BANNER : BannerSize

    /** 300x250. */
    data object MEDIUM_RECTANGLE : BannerSize

    /** Anchored adaptive - rộng theo màn hình, cao do mạng quyết định (khuyến nghị của AdMob). */
    data object ADAPTIVE : BannerSize
}
