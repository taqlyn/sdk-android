package com.taqlyn.sdk.adapters

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

interface EventsClient {
    suspend fun trackOpen(request: OpenEventRequest)

    suspend fun logEvent(request: SdkEventRequest)
}

data class OpenEventRequest(
    val apiBaseUrl: String,
    val clientId: String,
    val publicKeyId: String,
    val linkId: String,
    val anonymousId: String,
    val env: String? = null,
)

data class SdkEventRequest(
    val apiBaseUrl: String,
    val clientId: String,
    val publicKeyId: String,
    val name: String,
    val data: Map<String, Any>? = null,
    val env: String? = null,
)

class HttpEventsClient : EventsClient {
    override suspend fun trackOpen(request: OpenEventRequest) {
        runCatching {
            postJson(
                url = "${request.apiBaseUrl.trimEnd('/')}/v1/events/open",
                clientId = request.clientId,
                publicKeyId = request.publicKeyId,
                body =
                    JSONObject()
                        .put("clientId", request.clientId)
                        .put("publicKeyId", request.publicKeyId)
                        .put("linkId", request.linkId)
                        .put("anonymousId", request.anonymousId)
                        .apply {
                            if (request.env != null) put("env", request.env)
                        },
            )
        }
    }

    override suspend fun logEvent(request: SdkEventRequest) {
        runCatching {
            val dataJson =
                request.data?.let { map ->
                    JSONObject().apply {
                        for ((k, v) in map) {
                            when (v) {
                                is String -> put(k, v)
                                is Number -> put(k, v)
                                is Boolean -> put(k, v)
                                else -> put(k, v.toString())
                            }
                        }
                    }
                }
            postJson(
                url = "${request.apiBaseUrl.trimEnd('/')}/v1/events/sdk",
                clientId = request.clientId,
                publicKeyId = request.publicKeyId,
                body =
                    JSONObject()
                        .put("clientId", request.clientId)
                        .put("publicKeyId", request.publicKeyId)
                        .put("name", request.name)
                        .apply {
                            if (dataJson != null) put("data", dataJson)
                            if (request.env != null) put("env", request.env)
                        },
            )
        }
    }

    private fun postJson(
        url: String,
        clientId: String,
        publicKeyId: String,
        body: JSONObject,
    ) {
        val endpoint = URL(url)
        val conn =
            (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Taqlyn-Client-Id", clientId)
                setRequestProperty("X-Taqlyn-Public-Key-Id", publicKeyId)
            }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val stream =
                if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.use { /* fire-and-forget */ }
        } finally {
            conn.disconnect()
        }
    }
}
