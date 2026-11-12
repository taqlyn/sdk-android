package com.taqlyn.sdk

import com.google.common.truth.Truth.assertThat
import com.taqlyn.sdk.adapters.HttpResolveClient
import com.taqlyn.sdk.adapters.ResolveOutcome
import com.taqlyn.sdk.adapters.ResolveRequest
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
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
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serving =
            thread {
                server.accept().use { socket ->
                    // Drain the request headers and body before replying.
                    val input = socket.getInputStream()
                    val buf = ByteArray(8192)
                    input.read(buf)
                    val bytes = body.toByteArray()
                    socket.getOutputStream().apply {
                        write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(),
                        )
                        write(bytes)
                        flush()
                    }
                }
            }
        try {
            return HttpResolveClient().resolve(
                ResolveRequest(
                    apiBaseUrl = "http://127.0.0.1:${server.localPort}",
                    clientId = "app_test_demo",
                    publicKeyId = "pk_test_demo",
                    referrer = "click_id=clk_1",
                ),
            )
        } finally {
            serving.join(2_000)
            server.close()
        }
    }
}
