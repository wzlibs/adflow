package com.adflow.core.consent

/** Giả lập vùng địa lý để test flow consent (ví dụ EEA) trên thiết bị không ở vùng đó thật.
 * Chỉ có hiệu lực trên thiết bị đã đăng ký qua [testDeviceHashedIds]. */
data class ConsentDebugConfig(
    val geography: ConsentDebugGeography = ConsentDebugGeography.DISABLED,
    val testDeviceHashedIds: List<String> = emptyList(),
)

enum class ConsentDebugGeography { DISABLED, EEA, NOT_EEA }
