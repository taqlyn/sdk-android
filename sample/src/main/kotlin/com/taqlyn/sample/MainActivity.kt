package com.taqlyn.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.taqlyn.nav.compose.model.Campaign
import com.taqlyn.nav.compose.model.DeferredLink as NavDeferredLink
import com.taqlyn.nav.compose.model.MatchType as NavMatchType
import com.taqlyn.nav.compose.navigation2.Nav2DeepLinkNavigator
import com.taqlyn.sdk.DeferredLink
import com.taqlyn.sdk.SdkCore

/**
 * Feature proof harness — imports [SdkCore] + [Nav2DeepLinkNavigator] only
 * (never installreferrer).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SdkCore.onIntent(intent)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SampleApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SdkCore.onIntent(intent)
    }
}

@Composable
private fun SampleApp() {
    val navController = rememberNavController()
    val navigator =
        remember {
            Nav2DeepLinkNavigator(
                navControllerProvider = { navController },
                routeMapper = { link ->
                    val id =
                        link.params["sku"]
                            ?: link.params["id"]
                            ?: link.path.trim('/').substringAfterLast('/').takeIf { it.isNotBlank() }
                    if (id.isNullOrBlank()) null else "product/$id"
                },
            )
        }

    var status by remember { mutableStateOf("Resolving deferred…") }

    LaunchedEffect(Unit) {
        val deferred =
            runCatching { SdkCore.resolveDeferred() }
                .getOrElse { err ->
                    status = "resolveDeferred soft-failed: ${err.message}"
                    null
                }
        status =
            if (deferred != null) {
                "Deferred pending (not ready): ${deferred.path} id=${deferred.linkId}"
            } else {
                "No deferred link (organic / already resolved / soft-fail)"
            }
        // Simulate splash / auth complete → deliver pending once.
        SdkCore.setReadyForNavigation(true)
    }

    LaunchedEffect(Unit) {
        SdkCore.observeLinks().collect { link ->
            val navigated = navigator.navigate(link.toNavDeferredLink())
            if (navigated) {
                SdkCore.consume(link.linkId)
                status =
                    buildString {
                        appendLine("Navigated + consumed:")
                        appendLine("  path=${link.path}")
                        appendLine("  linkId=${link.linkId}")
                        appendLine("  deferred=${link.isDeferred}")
                        appendLine("  matchType=${link.matchType}")
                        appendLine("  url=${link.url}")
                    }
            } else {
                status = "Skipped navigate (unmapped or already consumed): ${link.linkId}"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Status: $status", style = MaterialTheme.typography.bodyMedium)
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                Text("Home", style = MaterialTheme.typography.headlineMedium)
            }
            composable(
                route = "product/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                Text("Product $id", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

/** Map SdkCore payload → nav-compose model (field copy + MatchType wire). */
private fun DeferredLink.toNavDeferredLink(): NavDeferredLink =
    NavDeferredLink(
        url = url,
        path = path,
        params = params,
        linkId = linkId,
        matchType = NavMatchType.fromWire(matchType.toWire()),
        isDeferred = isDeferred,
        campaign = campaign?.let { Campaign(it.values) },
    )
