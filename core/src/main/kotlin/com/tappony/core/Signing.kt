package com.tappony.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HMAC-SHA256 request signing. PROFILE_SCHEMA.md section 6. */
object Signing {
    const val TIMESTAMP_HEADER = "X-TapPony-Timestamp"
    const val NONCE_HEADER = "X-TapPony-Nonce"
    const val SIGNATURE_HEADER = "X-TapPony-Signature"

    fun signature(key: String, timestamp: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        // SecretKeySpec refuses an empty key. HMAC zero-pads keys to the block size,
        // so an empty key and a single 0x00 byte are the same key.
        val k = key.toByteArray(Charsets.UTF_8).let { if (it.isEmpty()) byteArrayOf(0) else it }
        mac.init(SecretKeySpec(k, "HmacSHA256"))
        val digest = mac.doFinal("$timestamp.$body".toByteArray(Charsets.UTF_8))
        return "sha256=" + Encoding.hexLower(digest)
    }
}
