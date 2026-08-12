# Taqlyn Android SDK (`sdk-android`)

Kotlin SdkCore + thin adapters for Play Install Referrer, App Links, resolve HTTP, and local prefs.

## Modules

| Path | Role |
|------|------|
| `taqlyn-sdk/` | Android library — public `SdkCore` + adapters |
| `sample/` | Proof harness app (imports **SdkCore** + **Nav2DeepLinkNavigator**) |

## Public API

```kotlin
SdkCore.configure(clientId, publicKeyId, options, context)
SdkCore.resolveDeferred()           // DeferredLink?
SdkCore.observeLinks()              // Flow<DeferredLink>
SdkCore.consume(linkId)
SdkCore.setReadyForNavigation(ready)
SdkCore.onIntent(intent)            // forward Activity App Links
```

`SdkOptions` includes `apiBaseUrl`, optional `linkProcessingMode` (`ALL` | `WEB_ONLY` | `DEFERRED_ONLY`), and optional `env`.

`DeferredLink` mirrors `packages/sdk-contract`: `url`, `path`, `params`, `linkId`, `matchType`, `isDeferred`, `campaign`.

## Wrappers (feature code must not import vendors)

| Adapter | Interface | Hides |
|---------|-----------|--------|
| `adapters/InstallReferrer.kt` | `InstallReferrer.readOnce()` | `com.android.installreferrer` |
| `adapters/IncomingLink.kt` | `IncomingLink.observe()` | Intent / App Links |
| `adapters/ResolveClient.kt` | `ResolveClient.resolve()` | HTTP `POST /v1/resolve` |
| `adapters/KeyValueStore.kt` | `KeyValueStore` | SharedPreferences |

Sample / app feature modules import `com.taqlyn.sdk.SdkCore` only (never Play Install Referrer). Navigation uses the optional `nav-compose` Nav2 adapter.

## Usage

```kotlin
class App : Application() {
  override fun onCreate() {
    super.onCreate()
    SdkCore.configure(
      clientId = "app_test_…",
      publicKeyId = "pk_test_…",
      options = SdkOptions(apiBaseUrl = "https://api.example.com"),
      context = this,
    )
  }
}

// After splash / auth:
lifecycleScope.launch {
  SdkCore.resolveDeferred()
  SdkCore.setReadyForNavigation(true)
}

val navigator = Nav2DeepLinkNavigator(
  navControllerProvider = { navController },
  routeMapper = { link -> /* map path/params → route */ },
)

SdkCore.observeLinks().onEach { link ->
  // Map SdkCore DeferredLink → nav-compose DeferredLink, then:
  if (navigator.navigate(navLink)) {
    SdkCore.consume(link.linkId)
  }
}.launchIn(scope)
```

The sample wires Compose `NavHost` (Home + `product/{id}`) to `Nav2DeepLinkNavigator` via Gradle `includeBuild("../nav-compose")` + dependency substitution for `com.taqlyn.nav:navigation2`.

## Unit tests

From this directory:

```bash
./gradlew :taqlyn-sdk:test
./gradlew :sample:assembleDebug
```

Coverage includes:

- resolve-once + local flag → second call `null`
- ready-gate holds pending until `setReadyForNavigation(true)`
- sample sources do not reference `com.android.installreferrer`
- Nav2 double-navigation guard by `linkId` (navigator)

## Real-device Install Referrer proof

**Emulators often cannot exercise Play Install Referrer.** Plan a real-device proof:

1. Publish (or sideload via Play internal testing) a build that includes this SDK.
2. Open a short link that 302s to Play with `referrer=click_id=…`.
3. Install from Play, cold-start the app.
4. Confirm `resolveDeferred()` returns the hydrated path/params once; second launch returns null.
5. Confirm sample/feature code has zero `com.android.installreferrer` imports.

Warm App Links can be smoke-tested with `adb shell am start -a android.intent.action.VIEW -d 'https://links.example.com/…'`.

## Branch

Develop on `integrate/phase-07-nav-adapters` (not `main`).
