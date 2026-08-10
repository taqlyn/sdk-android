package com.taqlyn.sdk.adapters

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Thin wrapper around Play Install Referrer.
 * Feature / sample code must never import `com.android.installreferrer`.
 */
fun interface InstallReferrer {
    /**
     * Read install referrer once and disconnect. Soft-fails to null on error.
     */
    suspend fun readOnce(): String?
}

/**
 * Production Play Install Referrer adapter.
 * Vendor types stay inside this file only.
 */
class PlayInstallReferrer(
    private val context: Context,
) : InstallReferrer {
    override suspend fun readOnce(): String? =
        suspendCancellableCoroutine { cont ->
            val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
            cont.invokeOnCancellation {
                runCatching { client.endConnection() }
            }
            try {
                client.startConnection(
                    object : InstallReferrerStateListener {
                        override fun onInstallReferrerSetupFinished(responseCode: Int) {
                            try {
                                if (responseCode != InstallReferrerClient.InstallReferrerResponse.OK) {
                                    if (cont.isActive) cont.resume(null)
                                    return
                                }
                                val details: ReferrerDetails = client.installReferrer
                                val referrer = details.installReferrer?.takeIf { it.isNotBlank() }
                                if (cont.isActive) cont.resume(referrer)
                            } catch (_: Exception) {
                                if (cont.isActive) cont.resume(null)
                            } finally {
                                runCatching { client.endConnection() }
                            }
                        }

                        override fun onInstallReferrerServiceDisconnected() {
                            // Soft-fail — caller may retry on next launch if flag unset.
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                )
            } catch (_: Exception) {
                runCatching { client.endConnection() }
                if (cont.isActive) cont.resume(null)
            }
        }
}

/**
 * Extract URL-decoded `click_id` from a Play referrer string
 * (e.g. `click_id=abc%20123&utm_source=…`).
 */
fun parseClickId(referrer: String): String? {
    val encoded =
        referrer
            .split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = part.substring(0, idx)
                val value = part.substring(idx + 1)
                key to value
            }.firstOrNull { it.first == "click_id" }
            ?.second
            ?: return null
    return runCatching { java.net.URLDecoder.decode(encoded, Charsets.UTF_8.name()) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}
