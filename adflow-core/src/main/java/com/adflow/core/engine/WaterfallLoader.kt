package com.adflow.core.engine

class WaterfallLoader<TAd>(
    private val adUnitIds: List<String>,
    private val attemptLoad: (adUnitId: String, onResult: (Result<TAd>) -> Unit) -> Unit,
) {
    fun start(onFinalResult: (Result<TAd>) -> Unit) {
        tryIndex(0, onFinalResult)
    }

    private fun tryIndex(index: Int, onFinalResult: (Result<TAd>) -> Unit) {
        if (index >= adUnitIds.size) {
            onFinalResult(Result.failure(NoSuchElementException("Waterfall exhausted: no ad units left")))
            return
        }
        attemptLoad(adUnitIds[index]) { result ->
            if (result.isSuccess) {
                onFinalResult(result)
            } else {
                tryIndex(index + 1, onFinalResult)
            }
        }
    }
}
