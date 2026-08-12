package com.taqlyn.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.taqlyn.sdk.adapters.HttpResolveClient
import com.taqlyn.sdk.adapters.IncomingLink
import com.taqlyn.sdk.adapters.InstallReferrer
import com.taqlyn.sdk.adapters.IntentIncomingLink
import com.taqlyn.sdk.adapters.KeyValueStore
import com.taqlyn.sdk.adapters.PlayInstallReferrer
import com.taqlyn.sdk.adapters.ResolveClient
import com.taqlyn.sdk.adapters.ResolveOutcome
import com.taqlyn.sdk.adapters.ResolveRequest
import com.taqlyn.sdk.adapters.SdkStoreKeys
import com.taqlyn.sdk.adapters.SharedPrefsKeyValueStore
import com.taqlyn.sdk.adapters.parseClickId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Canonical Android SdkCore facade.
 *
 * App feature modules import this type only — never Play Install Referrer /
 * raw HTTP / SharedPreferences vendor details.
 */
object SdkCore {
    private val mutex = Mutex()

    @Volatile
    private var configured: Config? = null

    @Volatile
    private var readyForNavigation: Boolean = false

    @Volatile
    private var pendingDeferred: DeferredLink? = null

    // replay=1 so collectors that subscribe after setReadyForNavigation still receive pending.
    private val deferredDelivery =
        MutableSharedFlow<DeferredLink>(replay = 1, extraBufferCapacity = 8)

    private data class Config(
        val clientId: String,
        val publicKeyId: String,
        val options: SdkOptions,
        val installReferrer: InstallReferrer,
        val resolveClient: ResolveClient,
        val store: KeyValueStore,
        val incomingLink: IncomingLink,
    )

    /**
     * Configure early in process lifetime (Application.onCreate).
     *
     * @param context Required for production adapters when custom deps are omitted.
     * @param installReferrer Injectable for tests; defaults to Play Install Referrer.
     * @param resolveClient Injectable for tests; defaults to HTTP POST /v1/resolve.
     * @param store Injectable for tests; defaults to SharedPreferences.
     * @param incomingLink Injectable for tests; defaults to intent App Links adapter.
     */
    @JvmStatic
    @JvmOverloads
    fun configure(
        clientId: String,
        publicKeyId: String,
        options: SdkOptions,
        context: Context? = null,
        installReferrer: InstallReferrer? = null,
        resolveClient: ResolveClient? = null,
        store: KeyValueStore? = null,
        incomingLink: IncomingLink? = null,
    ) {
        require(clientId.isNotBlank()) { "clientId required" }
        require(publicKeyId.isNotBlank()) { "publicKeyId required" }
        require(options.apiBaseUrl.isNotBlank()) { "options.apiBaseUrl required" }

        val appContext = context?.applicationContext
        val referrer =
            installReferrer
                ?: PlayInstallReferrer(
                    requireNotNull(appContext) {
                        "context required when installReferrer is not provided"
                    },
                )
        val kv =
            store
                ?: SharedPrefsKeyValueStore(
                    requireNotNull(appContext) {
                        "context required when store is not provided"
                    },
                )
        configured =
            Config(
                clientId = clientId,
                publicKeyId = publicKeyId,
                options = options,
                installReferrer = referrer,
                resolveClient = resolveClient ?: HttpResolveClient(),
                store = kv,
                incomingLink = incomingLink ?: IntentIncomingLink(),
            )
        readyForNavigation = false
        pendingDeferred = null
    }

    /**
     * Resolve deferred link once after install.
     *
     * - Returns null if already resolved locally, organic / empty referrer, no match, or soft-fail.
     * - On success queues the link until [setReadyForNavigation](true) for observers.
     * - Soft-fails network errors (never throws to crash launch); does not set local flag.
     */
    @JvmStatic
    suspend fun resolveDeferred(): DeferredLink? {
        val config = configured ?: return null
        if (config.options.linkProcessingMode == LinkProcessingMode.WEB_ONLY) {
            return null
        }

        return try {
            mutex.withLock {
                if (config.store.getBoolean(SdkStoreKeys.DEFERRED_RESOLVED, false)) {
                    return@withLock null
                }

                val referrer =
                    runCatching { config.installReferrer.readOnce() }.getOrNull()
                if (referrer.isNullOrBlank() || isOrganic(referrer)) {
                    config.store.putBoolean(SdkStoreKeys.DEFERRED_RESOLVED, true)
                    return@withLock null
                }

                // Prefer short click_id in referrer; full string still sent to API.
                parseClickId(referrer)

                val outcome =
                    runCatching {
                        config.resolveClient.resolve(
                            ResolveRequest(
                                apiBaseUrl = config.options.apiBaseUrl,
                                clientId = config.clientId,
                                publicKeyId = config.publicKeyId,
                                referrer = referrer,
                                env = config.options.env,
                            ),
                        )
                    }.getOrElse { ResolveOutcome.SoftFailure }

                when (outcome) {
                    is ResolveOutcome.SoftFailure -> null
                    is ResolveOutcome.NoMatch -> {
                        config.store.putBoolean(SdkStoreKeys.DEFERRED_RESOLVED, true)
                        null
                    }
                    is ResolveOutcome.Matched -> {
                        config.store.putBoolean(SdkStoreKeys.DEFERRED_RESOLVED, true)
                        pendingDeferred = outcome.link
                        if (readyForNavigation) {
                            deferredDelivery.tryEmit(outcome.link)
                        }
                        outcome.link
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stream of warm App Links and deferred links (deferred gated by ready flag).
     */
    @JvmStatic
    fun observeLinks(): Flow<DeferredLink> {
        val config = configured
        val mode = config?.options?.linkProcessingMode ?: LinkProcessingMode.ALL
        val warm: Flow<DeferredLink> =
            if (config != null && mode != LinkProcessingMode.DEFERRED_ONLY) {
                config.incomingLink.observe().map { uriToDeferred(it) }
            } else {
                emptyFlow()
            }
        val deferred: Flow<DeferredLink> =
            if (mode != LinkProcessingMode.WEB_ONLY) {
                flow {
                    val pending = pendingDeferred
                    if (readyForNavigation && pending != null) {
                        emit(pending)
                    }
                    deferredDelivery.asSharedFlow().collect { emit(it) }
                }
            } else {
                emptyFlow()
            }
        return merge(warm, deferred)
    }

    /** Clear pending deferred link when [linkId] matches. */
    @JvmStatic
    fun consume(linkId: String) {
        val pending = pendingDeferred
        if (pending != null && pending.linkId == linkId) {
            pendingDeferred = null
        }
    }

    /**
     * When true, deliver any pending deferred link to [observeLinks] once.
     */
    @JvmStatic
    fun setReadyForNavigation(ready: Boolean) {
        readyForNavigation = ready
        if (!ready) return
        val pending = pendingDeferred ?: return
        deferredDelivery.tryEmit(pending)
    }

    /** Forward Activity intents into the IncomingLink adapter (App Links). */
    @JvmStatic
    fun onIntent(intent: Intent?) {
        configured?.incomingLink?.onIntent(intent)
    }

    /** Test-only reset between unit tests (public for cross-module bridge hosts). */
    @JvmStatic
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun resetForTests() {
        configured = null
        readyForNavigation = false
        pendingDeferred = null
        deferredDelivery.resetReplayCache()
    }

    /** Peek pending deferred (tests; public for cross-module bridge hosts). */
    @JvmStatic
    fun pendingForTests(): DeferredLink? = pendingDeferred

    private fun isOrganic(referrer: String): Boolean {
        val trimmed = referrer.trim()
        return trimmed.equals("organic", ignoreCase = true)
    }

    private fun uriToDeferred(uri: Uri): DeferredLink {
        val params = mutableMapOf<String, String>()
        for (name in uri.queryParameterNames) {
            val value = uri.getQueryParameter(name) ?: continue
            params[name] = value
        }
        return DeferredLink(
            url = uri.toString(),
            path = uri.path ?: "/",
            params = params,
            linkId = params["linkId"] ?: params["link_id"] ?: uri.toString(),
            matchType = MatchType.NONE,
            isDeferred = false,
            campaign = null,
        )
    }
}
