package com.taqlyn.sdk.adapters

import com.taqlyn.sdk.ExpandResult
import com.taqlyn.sdk.ResolveMode
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

fun interface ExpandClient {
    suspend fun expand(request: ExpandRequest): ExpandOutcome
}

data class ExpandRequest(
    val apiBaseUrl: String,
    val clientId: String,
    val publicKeyId: String,
    val url: String,
    val env: String? = null,
)

sealed class ExpandOutcome {
    data class Ok(val result: ExpandResult) : ExpandOutcome()

    data object NotFound : ExpandOutcome()

    data object SoftFailure : ExpandOutcome()
}

class HttpExpandClient : ExpandClient {
    override suspend fun expand(request: ExpandRequest): ExpandOutcome {
        return try {
            val base = request.apiBaseUrl.trimEnd('/')
            val endpoint = URL("$base/v1/links/expand")
            val body =
                JSONObject()
                    .put("clientId", request.clientId)
                    .put("publicKeyId", request.publicKeyId)
                    .put("url", request.url)
                    .apply {
                        if (request.env != null) put("env", request.env)
                    }.toString()

            val conn =
                (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }

            try {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                when (conn.responseCode) {
                    in 200..299 -> {
                        val text =
                            conn.inputStream.bufferedReader().use(BufferedReader::readText)
                        if (text.isBlank()) return ExpandOutcome.NotFound
                        val parsed = parseExpand(JSONObject(text))
                        if (parsed == null) ExpandOutcome.NotFound else ExpandOutcome.Ok(parsed)
                    }
                    404 -> ExpandOutcome.NotFound
                    else -> ExpandOutcome.SoftFailure
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            ExpandOutcome.SoftFailure
        }
    }

    companion object {
        fun parseExpand(json: JSONObject): ExpandResult? {
            val linkId = json.optString("linkId", "").ifBlank { return null }
            val paramsObj = json.optJSONObject("params")
            val params = mutableMapOf<String, String>()
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    params[k] = paramsObj.optString(k, "")
                }
            }
            return ExpandResult(
                linkId = linkId,
                code = json.optString("code", ""),
                host = json.optString("host", ""),
                destinationPath = json.optString("destinationPath", "/"),
                params = params,
                url = json.optString("url", ""),
                linkResolveMode =
                    if (json.has("linkResolveMode") && !json.isNull("linkResolveMode")) {
                        ResolveMode.fromWire(json.getString("linkResolveMode"))
                    } else {
                        null
                    },
                appResolveMode =
                    if (json.has("appResolveMode") && !json.isNull("appResolveMode")) {
                        ResolveMode.fromWire(json.getString("appResolveMode"))
                    } else {
                        null
                    },
                appExpandTimeoutMs =
                    if (json.has("appExpandTimeoutMs") && !json.isNull("appExpandTimeoutMs")) {
                        json.optInt("appExpandTimeoutMs")
                    } else {
                        null
                    },
            )
        }
    }
}
