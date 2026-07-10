package com.adflow.core.config

fun interface AdRule {
    fun isAllowed(placementId: String): Boolean
}
