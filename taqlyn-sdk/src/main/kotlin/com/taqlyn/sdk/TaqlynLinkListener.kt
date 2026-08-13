package com.taqlyn.sdk

/**
 * Android-only custom listener for App Links + Play Install Referrer / claim deferred.
 *
 * Clipboard / App Clip matches are iOS-only and are never delivered here.
 * Feature code should not import Play Install Referrer — that stays in [adapters.InstallReferrer].
 */
fun interface TaqlynLinkListener {
    fun onTaqlynLink(link: DeferredLink)
}

/** Warm App Links, or deferred matches that Android can produce. */
fun isAndroidPlatformLink(link: DeferredLink): Boolean {
    if (!link.isDeferred) return true
    return when (link.matchType) {
        MatchType.INSTALL_REFERRER, MatchType.CLAIM -> true
        MatchType.CLIPBOARD, MatchType.APP_CLIP, MatchType.NONE -> false
    }
}
