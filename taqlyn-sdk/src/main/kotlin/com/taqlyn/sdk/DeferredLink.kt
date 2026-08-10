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
    NONE,
    ;

    fun toWire(): String =
        when (this) {
            INSTALL_REFERRER -> "install_referrer"
            CLIPBOARD -> "clipboard"
            APP_CLIP -> "app_clip"
            CLAIM -> "claim"
            NONE -> "none"
        }

    companion object {
        fun fromWire(value: String?): MatchType =
            when (value) {
                "install_referrer" -> INSTALL_REFERRER
                "clipboard" -> CLIPBOARD
                "app_clip" -> APP_CLIP
                "claim" -> CLAIM
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

/**
 * Configure options for [SdkCore.configure].
 *
 * @param apiBaseUrl Control-plane base URL (e.g. https://api.example.com)
 * @param linkProcessingMode Optional filter for observe/resolve delivery
 * @param env Optional environment hint forwarded to resolve (sandbox/live)
 */
data class SdkOptions(
    val apiBaseUrl: String,
    val linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL,
    val env: String? = null,
)
