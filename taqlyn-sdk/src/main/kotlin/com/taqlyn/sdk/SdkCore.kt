package com.taqlyn.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.taqlyn.sdk.adapters.EventsClient
import com.taqlyn.sdk.adapters.ExpandClient
import com.taqlyn.sdk.adapters.ExpandOutcome
import com.taqlyn.sdk.adapters.ExpandRequest
import com.taqlyn.sdk.adapters.HttpEventsClient
import com.taqlyn.sdk.adapters.HttpExpandClient
import com.taqlyn.sdk.adapters.OpenEventRequest
import com.taqlyn.sdk.adapters.SdkEventRequest
import com.taqlyn.sdk.adapters.HttpResolveClient
import com.taqlyn.sdk.adapters.HttpShareClient
import com.taqlyn.sdk.adapters.IncomingLink
import com.taqlyn.sdk.adapters.InstallReferrer
import com.taqlyn.sdk.adapters.IntentIncomingLink
import com.taqlyn.sdk.adapters.KeyValueStore
import com.taqlyn.sdk.adapters.PlayInstallReferrer
import com.taqlyn.sdk.adapters.ResolveClient
import com.taqlyn.sdk.adapters.ResolveOutcome
import com.taqlyn.sdk.adapters.ResolveRequest
import com.taqlyn.sdk.adapters.SdkStoreKeys
import com.taqlyn.sdk.adapters.ShareClient
import com.taqlyn.sdk.adapters.ShareLinkRequest
import com.taqlyn.sdk.adapters.SharedPrefsKeyValueStore
import com.taqlyn.sdk.adapters.parseClickId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

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

    private val listenerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listenerJobs = ConcurrentHashMap<TaqlynLinkListener, Job>()

    @Volatile
    private var anonymousId: String? = null

    private data class Config(
        val clientId: String,
        val publicKeyId: String,
        val options: SdkOptions,
        val installReferrer: InstallReferrer,
        val resolveClient: ResolveClient,
        val expandClient: ExpandClient,
        val shareClient: ShareClient,
        val eventsClient: EventsClient,
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
        expandClient: ExpandClient? = null,
        shareClient: ShareClient? = null,
        eventsClient: EventsClient? = null,
        store: KeyValueStore? = null,
        incomingLink: IncomingLink? = null,
    ) {
        require(clientId.isNotBlank()) { "clientId required" }
        require(publicKeyId.isNotBlank()) { "publicKeyId required" }

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
                expandClient = expandClient ?: HttpExpandClient(),
                shareClient = shareClient ?: HttpShareClient(),
                eventsClient = eventsClient ?: HttpEventsClient(),
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
                val organic = referrer.isNullOrBlank() || isOrganic(referrer)
                if (organic && !config.options.enableProbabilisticMatch) {
                    config.store.putBoolean(SdkStoreKeys.DEFERRED_RESOLVED, true)
                    return@withLock null
                }

                if (!organic) {
                    parseClickId(referrer!!)
                }

                val outcome =
                    runCatching {
                        config.resolveClient.resolve(
                            ResolveRequest(
                                apiBaseUrl = TaqlynApi.origin,
                                clientId = config.clientId,
                                publicKeyId = config.publicKeyId,
                                referrer = if (organic) "" else referrer.orEmpty(),
                                env = config.options.env,
                                fingerprint = organic && config.options.enableProbabilisticMatch,
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
     * Android-only custom listener (App Links + Install Referrer / claim).
     * Clipboard / App Clip matches are not delivered.
     */
    @JvmStatic
    fun addLinkListener(listener: TaqlynLinkListener): AutoCloseable {
        val job =
            listenerScope.launch {
                observeLinks().collect { link ->
                    if (isAndroidPlatformLink(link)) listener.onTaqlynLink(link)
                }
            }
        listenerJobs[listener] = job
        return AutoCloseable {
            job.cancel()
            listenerJobs.remove(listener, job)
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

    /**
     * Expand a short URL to an in-app path (POST /v1/links/expand).
     * Returns null on not found / soft failure.
     */
    @JvmStatic
    suspend fun resolveShortLink(url: String): ExpandResult? {
        val config = configured ?: return null
        return when (
            val outcome =
                config.expandClient.expand(
                    ExpandRequest(
                        apiBaseUrl = TaqlynApi.origin,
                        clientId = config.clientId,
                        publicKeyId = config.publicKeyId,
                        url = url,
                        env = config.options.env,
                    ),
                )
        ) {
            is ExpandOutcome.Ok -> outcome.result
            ExpandOutcome.NotFound, ExpandOutcome.SoftFailure -> null
        }
    }

    /**
     * Map an incoming URL to a nav target using hosts + short-code heuristic and
     * resolveMode cascade (link → app → client → await).
     */
    @JvmStatic
    suspend fun resolveIncomingUrl(url: String): ResolvedNavTarget? {
        val config = configured ?: return pathMapOnly(url)
        if (!shouldExpand(url, config.options.hosts)) {
            return pathMapOnly(url)
        }
        val fallback = config.options.fallbackPath?.ifBlank { "/" } ?: "/"
        // Client mode gates first paint; link/app overrides apply on subsequent opens
        // once expand metadata is known (returned on ExpandResult for callers).
        val clientMode = config.options.resolveMode ?: ResolveMode.AWAIT
        if (clientMode == ResolveMode.OPTIMISTIC) {
            return ResolvedNavTarget(href = fallback, pendingExpand = true)
        }
        val timeoutMs = config.options.timeoutMs.coerceAtLeast(100L)
        val result = withTimeoutOrNull(timeoutMs) { resolveShortLink(url) }
        // Prefer link/app mode from response only to refine timeout re-entry; destination wins.
        if (result != null) {
            val effective =
                result.linkResolveMode
                    ?: result.appResolveMode
                    ?: clientMode
            if (effective == ResolveMode.OPTIMISTIC && result.appExpandTimeoutMs != null) {
                // Already have destination from await — return it (no flash).
            }
            return ResolvedNavTarget(href = hrefFromExpand(result), linkId = result.linkId)
        }
        return ResolvedNavTarget(href = fallback)
    }

    /** Mint a unified short link for in-app sharing (public key id only). */
    @JvmStatic
    suspend fun createShareLink(
        destinationPath: String? = null,
        destinationWeb: String? = null,
        params: Map<String, String>? = null,
        ogTitle: String? = null,
        ogDescription: String? = null,
        ogImage: String? = null,
    ): ShareLink {
        val config = configured ?: throw ShareLinkException("configure before createShareLink")
        return config.shareClient.create(
            ShareLinkRequest(
                apiBaseUrl = TaqlynApi.origin,
                clientId = config.clientId,
                publicKeyId = config.publicKeyId,
                destinationPath = destinationPath,
                destinationWeb = destinationWeb,
                params = params,
                env = config.options.env,
                ogTitle = ogTitle,
                ogDescription = ogDescription,
                ogImage = ogImage,
            ),
        )
    }

    /** Report that the SDK opened/consumed a link (fire-and-forget). */
    @JvmStatic
    fun trackOpen(linkId: String) {
        val config = configured ?: return
        val id = linkId.trim()
        if (id.isEmpty()) return
        listenerScope.launch {
            config.eventsClient.trackOpen(
                OpenEventRequest(
                    apiBaseUrl = TaqlynApi.origin,
                    clientId = config.clientId,
                    publicKeyId = config.publicKeyId,
                    linkId = id,
                    anonymousId = deviceAnonymousId(),
                    env = config.options.env,
                ),
            )
        }
    }

    /** Custom SDK analytics event (fire-and-forget). */
    @JvmStatic
    fun logEvent(name: String, data: Map<String, Any>? = null) {
        val config = configured ?: return
        val eventName = name.trim()
        if (eventName.isEmpty()) return
        listenerScope.launch {
            config.eventsClient.logEvent(
                SdkEventRequest(
                    apiBaseUrl = TaqlynApi.origin,
                    clientId = config.clientId,
                    publicKeyId = config.publicKeyId,
                    name = eventName,
                    data = data,
                    env = config.options.env,
                ),
            )
        }
    }

    /** Clear pending deferred link when [linkId] matches. */
    @JvmStatic
    fun consume(linkId: String) {
        val pending = pendingDeferred
        if (pending != null && pending.linkId == linkId) {
            pendingDeferred = null
        }
        trackOpen(linkId)
    }

    private fun deviceAnonymousId(): String {
        anonymousId?.let { return it }
        val id = "anon_${System.currentTimeMillis()}_${(0..9999).random()}"
        anonymousId = id
        return id
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
        listenerJobs.values.forEach { it.cancel() }
        listenerJobs.clear()
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

    private fun shouldExpand(url: String, hosts: List<String>): Boolean {
        if (hosts.isEmpty()) return looksLikeShortCode(url)
        val host =
            runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        val hostOk =
            hosts.any { prefix ->
                val p = prefix.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')
                host == p || host.endsWith(".$p") || url.startsWith(prefix)
            }
        return hostOk && looksLikeShortCode(url)
    }

    private fun looksLikeShortCode(url: String): Boolean {
        val path = runCatching { URI(url).path?.trim('/') }.getOrNull() ?: return false
        if (path.isBlank() || path.contains('/')) return false
        return path.matches(Regex("^[A-Za-z0-9_-]{4,32}$"))
    }

    private fun pathMapOnly(url: String): ResolvedNavTarget? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val path = uri.path?.ifBlank { "/" } ?: "/"
        val qs = uri.encodedQuery
        val href = if (qs.isNullOrBlank()) path else "$path?$qs"
        return ResolvedNavTarget(href = href)
    }

    private fun hrefFromExpand(result: ExpandResult): String {
        val path =
            if (result.destinationPath.startsWith("/")) {
                result.destinationPath
            } else {
                "/${result.destinationPath}"
            }
        if (result.params.isEmpty()) return path
        val qs =
            result.params.entries.joinToString("&") { (k, v) ->
                "${Uri.encode(k)}=${Uri.encode(v)}"
            }
        return "$path?$qs"
    }
}
