package com.taqlyn.sample

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taqlyn.sdk.DeferredLink
import com.taqlyn.sdk.SdkCore
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Feature proof harness — imports [SdkCore] only (never installreferrer).
 */
class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)

        SdkCore.onIntent(intent)

        SdkCore.observeLinks()
            .onEach { link -> onLink(link) }
            .launchIn(lifecycleScope)

        lifecycleScope.launch {
            status.text = "Resolving deferred…"
            val deferred =
                runCatching { SdkCore.resolveDeferred() }
                    .getOrElse { err ->
                        status.text = "resolveDeferred soft-failed: ${err.message}"
                        null
                    }
            status.text =
                if (deferred != null) {
                    "Deferred pending (not ready): ${deferred.path} id=${deferred.linkId}"
                } else {
                    "No deferred link (organic / already resolved / soft-fail)"
                }
            // Simulate splash / auth complete → deliver pending once.
            SdkCore.setReadyForNavigation(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SdkCore.onIntent(intent)
    }

    private fun onLink(link: DeferredLink) {
        status.text =
            buildString {
                appendLine("Delivered link:")
                appendLine("  path=${link.path}")
                appendLine("  linkId=${link.linkId}")
                appendLine("  deferred=${link.isDeferred}")
                appendLine("  matchType=${link.matchType}")
                appendLine("  url=${link.url}")
            }
        SdkCore.consume(link.linkId)
    }
}
