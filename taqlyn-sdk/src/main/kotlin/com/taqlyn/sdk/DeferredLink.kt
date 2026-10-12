package com.taqlyn.sdk

/**
 * Canonical resolve payload — mirrors packages/sdk-contract DeferredLink.
 */
data class DeferredLink(
    val url: String,
    val path: String,
    val params: Map<String, String> = emptyMap(),
    val linkId: String,
    val matchType: MatchType,
    val isDeferred: Boolean,
    val campaign: Campaign? = null,
)

enum class MatchType {
    INSTALL_REFERRER,
    CLIPBOARD,
    APP_CLIP,
    CLAIM,
    FINGERPRINT,
    NONE,
    ;

    fun toWire(): String =
        when (this) {
            INSTALL_REFERRER -> "install_referrer"
            CLIPBOARD -> "clipboard"
            APP_CLIP -> "app_clip"
            CLAIM -> "claim"
            FINGERPRINT -> "fingerprint"
            NONE -> "none"
        }

    companion object {
        fun fromWire(value: String?): MatchType =
            when (value) {
                "install_referrer" -> INSTALL_REFERRER
                "clipboard" -> CLIPBOARD
                "app_clip" -> APP_CLIP
                "claim" -> CLAIM
                "fingerprint" -> FINGERPRINT
                "none" -> NONE
                else -> NONE
            }
    }
}

/** Optional UTM / campaign attribution. */
data class Campaign(
    val values: Map<String, String> = emptyMap(),
) {
    val utmSource: String? get() = values["utm_source"]
    val utmCampaign: String? get() = values["utm_campaign"]
}

/** How SdkCore should process incoming / deferred links. */
enum class LinkProcessingMode {
    ALL,
    WEB_ONLY,
    DEFERRED_ONLY,
}

/** Gap 1 — short-link expand UX (await | optimistic). */
enum class ResolveMode {
    AWAIT,
    OPTIMISTIC,
    ;

    fun toWire(): String =
        when (this) {
            AWAIT -> "await"
            OPTIMISTIC -> "optimistic"
        }

    companion object {
        fun fromWire(value: String?): ResolveMode? =
            when (value?.trim()?.lowercase()) {
                "await" -> AWAIT
                "optimistic" -> OPTIMISTIC
                else -> null
            }
    }
}

/** Response from POST /v1/links/expand. */
data class ExpandResult(
    val linkId: String,
    val code: String,
    val host: String,
    val destinationPath: String,
    val params: Map<String, String> = emptyMap(),
    val url: String,
    val linkResolveMode: ResolveMode? = null,
    val appResolveMode: ResolveMode? = null,
    val appExpandTimeoutMs: Int? = null,
)

/** Nav target after expand / path-map. */
data class ResolvedNavTarget(
    val href: String,
    val linkId: String? = null,
    val pendingExpand: Boolean = false,
)

/**
 * Configure options for [SdkCore.configure].
 *
 * The control-plane origin is baked into the SDK (`https://api.taqlyn.com`,
 * or `TAQLYN_API_BASE_URL` when compiling/publishing this library).
 *
 * @param linkProcessingMode Optional filter for observe/resolve delivery
 * @param env Optional environment hint forwarded to resolve (sandbox/live)
 * @param resolveMode Client default for short-link expand (cascade with link/app)
 * @param fallbackPath Optional default backstack/home when expand fails or optimistic
 * @param hosts Host prefixes that may need short-link expand
 * @param timeoutMs Client await timeout; app expandTimeoutMs overrides when set
 */
data class SdkOptions(
    val linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL,
    val env: String? = null,
    val resolveMode: ResolveMode? = null,
    val fallbackPath: String? = "/",
    val hosts: List<String> = emptyList(),
    val timeoutMs: Long = 1500L,
    /**
     * Android-only: when Install Referrer is empty, try probabilistic IP-hash match.
     * Off by default (privacy-first). Never enable on iOS hosts.
     */
    val enableProbabilisticMatch: Boolean = false,
)

/** Unified short link minted from the mobile SDK (public key id only). */
data class ShareLink(
    val id: String,
    val code: String,
    val shortUrl: String,
    val host: String,
    val env: String,
)

class ShareLinkException(message: String) : Exception(message)
