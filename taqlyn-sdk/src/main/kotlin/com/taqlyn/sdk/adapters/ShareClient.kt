package com.taqlyn.sdk.adapters

import com.taqlyn.sdk.ShareLink
import com.taqlyn.sdk.ShareLinkException
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** HTTP client for in-app share create — hides URLConnection from feature code. */
fun interface ShareClient {
    suspend fun create(request: ShareLinkRequest): ShareLink
}

data class ShareLinkRequest(
    val apiBaseUrl: String,
    val clientId: String,
    val publicKeyId: String,
    val destinationPath: String? = null,
    val destinationWeb: String? = null,
    val params: Map<String, String>? = null,
    val env: String? = null,
    val ogTitle: String? = null,
    val ogDescription: String? = null,
    val ogImage: String? = null,
)

/** Production [ShareClient] using HttpURLConnection. */
class HttpShareClient : ShareClient {
    override suspend fun create(request: ShareLinkRequest): ShareLink {
        val path = request.destinationPath?.trim().orEmpty()
        val web = request.destinationWeb?.trim().orEmpty()
        if (path.isEmpty() && web.isEmpty()) {
            throw ShareLinkException("destinationPath or destinationWeb required")
        }
        val base = request.apiBaseUrl.trimEnd('/')
        val url = URL("$base/v1/sdk/short-links")
        val body =
            JSONObject()
                .put("clientId", request.clientId)
                .put("publicKeyId", request.publicKeyId)
                .apply {
                    if (path.isNotEmpty()) put("destinationPath", path)
                    if (web.isNotEmpty()) put("destinationWeb", web)
                    request.env?.let { put("env", it) }
                    request.ogTitle?.let { put("ogTitle", it) }
                    request.ogDescription?.let { put("ogDescription", it) }
                    request.ogImage?.let { put("ogImage", it) }
                    if (!request.params.isNullOrEmpty()) {
                        put("params", JSONObject(request.params))
                    }
                }.toString()

        val conn =
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Taqlyn-Client-Id", request.clientId)
                setRequestProperty("X-Taqlyn-Public-Key-Id", request.publicKeyId)
            }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val text =
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.use(BufferedReader::readText)
                    .orEmpty()
            if (code !in 200..299) {
                throw ShareLinkException("createShareLink failed: $code")
            }
            val json = JSONObject(text)
            return ShareLink(
                id = json.optString("id"),
                code = json.optString("code"),
                shortUrl = json.optString("shortUrl"),
                host = json.optString("host"),
                env = json.optString("env"),
            )
        } finally {
            conn.disconnect()
        }
    }
}
