package com.taqlyn.sdk.adapters

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Warm App Links / intent URI stream.
 * Feature code observes via [com.taqlyn.sdk.SdkCore.observeLinks], not this type.
 */
interface IncomingLink {
    /** Emits verified / cold-start App Link URIs. */
    fun observe(): Flow<Uri>

    /** Forward Activity intent data (call from onCreate / onNewIntent). */
    fun onIntent(intent: Intent?)
}

/**
 * Default in-process App Links adapter.
 */
class IntentIncomingLink : IncomingLink {
    // replay=1 so collectors that subscribe after onCreate/onNewIntent still see the URI.
    private val _uris = MutableSharedFlow<Uri>(replay = 1, extraBufferCapacity = 16)

    override fun observe(): Flow<Uri> = _uris.asSharedFlow()

    override fun onIntent(intent: Intent?) {
        val data = intent?.data ?: return
        _uris.tryEmit(data)
    }
}
