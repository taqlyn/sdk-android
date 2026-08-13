package com.taqlyn.sdk

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.taqlyn.sdk.adapters.InMemoryKeyValueStore
import com.taqlyn.sdk.adapters.InstallReferrer
import com.taqlyn.sdk.adapters.IntentIncomingLink
import com.taqlyn.sdk.adapters.ResolveClient
import com.taqlyn.sdk.adapters.ResolveOutcome
import com.taqlyn.sdk.adapters.ResolveRequest
import com.taqlyn.sdk.adapters.SdkStoreKeys
import com.taqlyn.sdk.adapters.parseClickId
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SdkCoreTest {
    private val store = InMemoryKeyValueStore()
    private val incoming = IntentIncomingLink()

    @After
    fun tearDown() {
        SdkCore.resetForTests()
    }

    @Test
    fun parseClickId_urlDecodesValue() {
        assertThat(parseClickId("click_id=abc%2F123&utm_source=x")).isEqualTo("abc/123")
        assertThat(parseClickId("utm_source=x")).isNull()
    }

    @Test
    fun resolveDeferred_onceThenNull_setsLocalFlag() =
        runTest {
            val link = sampleLink("lnk_1")
            configureWith(
                referrer = "click_id=clk_1",
                resolve = { ResolveOutcome.Matched(link) },
            )

            val first = SdkCore.resolveDeferred()
            val second = SdkCore.resolveDeferred()

            assertThat(first).isEqualTo(link)
            assertThat(second).isNull()
            assertThat(store.getBoolean(SdkStoreKeys.DEFERRED_RESOLVED)).isTrue()
        }

    @Test
    fun resolveDeferred_softFailure_doesNotSetFlag() =
        runTest {
            configureWith(
                referrer = "click_id=clk_1",
                resolve = { ResolveOutcome.SoftFailure },
            )

            assertThat(SdkCore.resolveDeferred()).isNull()
            assertThat(store.getBoolean(SdkStoreKeys.DEFERRED_RESOLVED)).isFalse()
        }

    @Test
    fun readyGate_holdsPendingUntilSetReadyForNavigation() =
        runTest {
            val link = sampleLink("lnk_ready")
            configureWith(
                referrer = "click_id=clk_ready",
                resolve = { ResolveOutcome.Matched(link) },
            )

            assertThat(SdkCore.resolveDeferred()).isEqualTo(link)
            assertThat(SdkCore.pendingForTests()).isEqualTo(link)

            // Before ready: pending held, not delivered via a completed collection yet.
            assertThat(SdkCore.pendingForTests()).isEqualTo(link)

            val deferred =
                async {
                    withTimeout(2_000) {
                        SdkCore.observeLinks().first { it.linkId == "lnk_ready" }
                    }
                }
            // Allow collector to subscribe under test scheduler.
            testScheduler.runCurrent()
            assertThat(deferred.isCompleted).isFalse()

            SdkCore.setReadyForNavigation(true)
            testScheduler.runCurrent()
            assertThat(deferred.await()).isEqualTo(link)

            SdkCore.consume("lnk_ready")
            assertThat(SdkCore.pendingForTests()).isNull()
        }

    @Test
    fun consume_clearsOnlyMatchingPending() =
        runTest {
            val link = sampleLink("lnk_a")
            configureWith(
                referrer = "click_id=x",
                resolve = { ResolveOutcome.Matched(link) },
            )
            SdkCore.resolveDeferred()
            SdkCore.consume("other")
            assertThat(SdkCore.pendingForTests()).isEqualTo(link)
            SdkCore.consume("lnk_a")
            assertThat(SdkCore.pendingForTests()).isNull()
        }

    @Test
    fun organicReferrer_setsFlagAndReturnsNull() =
        runTest {
            configureWith(
                referrer = "organic",
                resolve = { error("should not resolve") },
            )
            assertThat(SdkCore.resolveDeferred()).isNull()
            assertThat(store.getBoolean(SdkStoreKeys.DEFERRED_RESOLVED)).isTrue()
        }

    @Test
    fun androidLinkListener_referrerDeferredAndSkipsClipboard() =
        runTest {
            assertThat(
                isAndroidPlatformLink(
                    sampleLink("r").copy(matchType = MatchType.INSTALL_REFERRER),
                ),
            ).isTrue()
            assertThat(
                isAndroidPlatformLink(
                    sampleLink("c").copy(matchType = MatchType.CLIPBOARD, isDeferred = true),
                ),
            ).isFalse()

            val link = sampleLink("lnk_listener")
            configureWith(
                referrer = "click_id=clk_listener",
                resolve = { ResolveOutcome.Matched(link) },
            )

            val received = mutableListOf<DeferredLink>()
            val closeable = SdkCore.addLinkListener { received += it }
            try {
                SdkCore.resolveDeferred()
                SdkCore.setReadyForNavigation(true)
                testScheduler.runCurrent()
                testScheduler.advanceUntilIdle()
                assertThat(received.map { it.linkId }).contains("lnk_listener")
                assertThat(received.first().matchType).isEqualTo(MatchType.INSTALL_REFERRER)
            } finally {
                closeable.close()
            }
        }

    @Test
    fun warmAppLink_deliversViaObserveLinks() =
        runTest {
            configureWith(
                referrer = null,
                resolve = { error("warm path must not call resolve") },
            )
            val uri = Uri.parse("https://go.example.com/offer?sku=42&linkId=lnk_warm")
            SdkCore.onIntent(Intent(Intent.ACTION_VIEW, uri))
            val link =
                withTimeout(2_000) {
                    SdkCore.observeLinks().first { it.linkId == "lnk_warm" }
                }
            assertThat(link.path).isEqualTo("/offer")
            assertThat(link.params["sku"]).isEqualTo("42")
            assertThat(link.isDeferred).isFalse()
            assertThat(link.matchType).isEqualTo(MatchType.NONE)
        }

    private fun configureWith(
        referrer: String?,
        resolve: suspend (ResolveRequest) -> ResolveOutcome,
    ) {
        val installReferrer = InstallReferrer { referrer }
        val resolveClient = ResolveClient { request -> resolve(request) }
        SdkCore.configure(
            clientId = "app_test_demo",
            publicKeyId = "pk_test_demo",
            options = SdkOptions(apiBaseUrl = "https://api.example.test"),
            context = null,
            installReferrer = installReferrer,
            resolveClient = resolveClient,
            store = store,
            incomingLink = incoming,
        )
    }

    private fun sampleLink(id: String) =
        DeferredLink(
            url = "https://app.example.com/offer?id=1",
            path = "/offer",
            params = mapOf("id" to "1"),
            linkId = id,
            matchType = MatchType.INSTALL_REFERRER,
            isDeferred = true,
            campaign = Campaign(mapOf("utm_source" to "invite")),
        )
}
