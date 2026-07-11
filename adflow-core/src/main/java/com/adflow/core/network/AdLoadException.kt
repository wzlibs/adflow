package com.adflow.core.network

import com.adflow.core.AdFlowError

/** Adapter throw exception này từ `load()` khi 1 ad unit không có fill/lỗi - engine bắt để rơi
 * xuống ad unit kế tiếp trong waterfall. */
class AdLoadException(val error: AdFlowError) : Exception(error.message)
