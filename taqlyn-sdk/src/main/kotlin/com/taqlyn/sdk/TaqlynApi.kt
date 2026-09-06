package com.taqlyn.sdk

/**
 * Hosted control-plane origin.
 *
 * App code cannot override this. At library compile / publish, set
 * `TAQLYN_API_BASE_URL` so Gradle bakes [BuildConfig.API_BASE_URL]; otherwise
 * [HOSTED_ORIGIN] is used.
 */
internal object TaqlynApi {
    const val HOSTED_ORIGIN = "https://api.taqlyn.com"

    val origin: String
        get() {
            val baked = BuildConfig.API_BASE_URL.trim().trimEnd('/')
            return baked.ifBlank { HOSTED_ORIGIN }
        }
}
