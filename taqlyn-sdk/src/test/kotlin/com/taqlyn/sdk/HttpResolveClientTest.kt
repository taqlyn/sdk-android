package com.taqlyn.sdk

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import com.taqlyn.sdk.adapters.HttpResolveClient
import com.taqlyn.sdk.adapters.ResolveOutcome
import com.taqlyn.sdk.adapters.ResolveRequest
import java.net.InetSocketAddress
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HttpResolveClientTest {
    @Test
    fun wrappedDeferredLink_isMatched() =
        runTest {
            val outcome =
                resolveWith(
                    """{"deferredLink":{"url":"https://go.example.com/home","path":"/home","params":{"from":"share"},"linkId":"sl_1","matchType":"install_referrer","isDeferred":true}}""",
                )

            assertThat(outcome).isInstanceOf(ResolveOutcome.Matched::class.java)
            val link = (outcome as ResolveOutcome.Matched).link
            assertThat(link.path).isEqualTo("/home")
            assertThat(link.linkId).isEqualTo("sl_1")
            assertThat(link.matchType).isEqualTo(MatchType.INSTALL_REFERRER)
        }

    @Test
    fun nullDeferredLink_isNoMatch() =
        runTest {
            assertThat(resolveWith("""{"deferredLink":null}""")).isEqualTo(ResolveOutcome.NoMatch)
        }

    private suspend fun resolveWith(body: String): ResolveOutcome {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/resolve") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            return HttpResolveClient().resolve(
                ResolveRequest(
                    apiBaseUrl = "http://127.0.0.1:${server.address.port}",
                    clientId = "app_test_demo",
                    publicKeyId = "pk_test_demo",
                    referrer = "click_id=clk_1",
                ),
            )
        } finally {
            server.stop(0)
        }
    }
}
