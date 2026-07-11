package com.adflow.core.config

/**
 * Điều kiện app tự định nghĩa để chặn load/show có điều kiện (ví dụ user đã mua premium thì trả
 * về false) - gán qua `loadWhen {}` / `showWhen {}` trong DSL khai báo placement.
 */
fun interface AdRule {
    fun isAllowed(placementId: String): Boolean
}
