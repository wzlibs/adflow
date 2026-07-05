package com.adflow.core

sealed interface AdLoadResult {
    data object Success : AdLoadResult
    data class Failure(val error: AdFlowError) : AdLoadResult
}
