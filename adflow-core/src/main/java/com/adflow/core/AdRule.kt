package com.adflow.core

fun interface AdRule {
    fun isAllowed(placementId: String): Boolean
}
