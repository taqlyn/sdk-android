package com.taqlyn.sdk.adapters

import com.taqlyn.sdk.Campaign
import com.taqlyn.sdk.DeferredLink
import com.taqlyn.sdk.MatchType
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * HTTP client for Match.resolve — hides URLConnection from feature code.
 */
fun interface ResolveClient {
    /**
     * POST /v1/resolve.
     * Soft-failures (network / 5xx) return [ResolveOutcome.SoftFailure] so the
     * local resolved-once flag is left unset for retry.
     */
    suspend fun resolve(request: ResolveRequest): ResolveOutcome
}

data class ResolveRequest(
    val apiBaseUrl: String,
    val clientId: String,
    val publicKeyId: String,
    val referrer: String,
    val env: String? = null,
)

sealed class ResolveOutcome {
    data class Matched(val link: DeferredLink) : ResolveOutcome()

    /** Completed call with no deferred payload (404 / 204 / empty). */
    data object NoMatch : ResolveOutcome()

    /** Transient failure — caller should not set resolved-once. */
    data object SoftFailure : ResolveOutcome()
}

/**
 * Production [ResolveClient] using HttpURLConnection (no extra HTTP dependency).
 */
class HttpResolveClient : ResolveClient {
    override suspend fun resolve(request: ResolveRequest): ResolveOutcome {
        return try {
            val base = request.apiBaseUrl.trimEnd('/')
            val url = URL("$base/v1/resolve")
            val body =
                JSONObject()
                    .put("clientId", request.clientId)
                    .put("publicKeyId", request.publicKeyId)
                    .put("referrer", request.referrer)
                    .apply {
                        if (request.env != null) put("env", request.env)
                    }.toString()

            val conn =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }

            try {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                when (val code = conn.responseCode) {
                    in 200..299 -> {
                        val text =
                            conn.inputStream.bufferedReader().use(BufferedReader::readText)
                        if (text.isBlank()) return ResolveOutcome.NoMatch
                        val link = parseDeferredLink(JSONObject(text))
                        if (link == null) ResolveOutcome.NoMatch else ResolveOutcome.Matched(link)
                    }
                    404, 204, 410 -> ResolveOutcome.NoMatch
                    else -> ResolveOutcome.SoftFailure
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            ResolveOutcome.SoftFailure
        }
    }

    companion object {
        fun parseDeferredLink(json: JSONObject): DeferredLink? {
            val linkId = json.optString("linkId", "").ifBlank { return null }
            val url = json.optString("url", "")
            val path = json.optString("path", "")
            val paramsObj = json.optJSONObject("params")
            val params = mutableMapOf<String, String>()
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    params[k] = paramsObj.optString(k, "")
                }
            }
            val campaignObj = json.optJSONObject("campaign")
            val campaign =
                if (campaignObj != null) {
                    val map = mutableMapOf<String, String>()
                    val keys = campaignObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (!campaignObj.isNull(k)) {
                            map[k] = campaignObj.optString(k, "")
                        }
                    }
                    Campaign(map)
                } else {
                    null
                }
            return DeferredLink(
                url = url,
                path = path,
                params = params,
                linkId = linkId,
                matchType = MatchType.fromWire(json.optString("matchType", "none")),
                isDeferred = json.optBoolean("isDeferred", true),
                campaign = campaign,
            )
        }
    }
}
