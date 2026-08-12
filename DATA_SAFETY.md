# Play Data safety — Taqlyn Android SDK

Customer-facing disclosure sheet for Google Play Console **Data safety** forms when an app ships `com.taqlyn.sdk` (Taqlyn Android SDK).

**NSPrivacyTracking / ads:** The SDK does **not** sell data, does **not** use data for advertising, and does **not** enable fingerprinting as a primary match method. Android deferred open prefers **Play Install Referrer**.

## Data collected by the SDK

| Data type (Play category) | Collected? | When | Why | Shared with Taqlyn backend? |
|---------------------------|------------|------|-----|-------------------------------|
| App activity — App interactions | Yes (install referrer string / `click_id`) | First open resolve | Deferred deep link matching | Yes — resolve API |
| Device or other IDs | No by default (no advertising ID / AAID required) | — | — | — |
| Location | No | — | — | — |
| Personal info (name, email) | No (SDK does not read account email) | — | — | — |
| Clipboard | Optional only if host app enables a clipboard adapter | Opt-in cascade step | Deterministic token handoff | Yes — token only if present |
| Diagnostics / crash | No (SDK does not embed a crash reporter) | — | — | — |

## Ephemeral / local only

| Data | Storage | Notes |
|------|---------|-------|
| “Already resolved” flag | App local prefs (`SharedPreferences` / DataStore via SDK wrapper) | Prevents re-resolve after first successful match |
| Pending deferred link | In-memory until `setReadyForNavigation` | Not uploaded as a separate analytics stream |

## Server-side (not collected by the SDK binary, but relevant to your form)

When the host app’s short links are opened on the web/edge before install, Taqlyn may store **truncated+hashed IP** and User-Agent on the click row (short TTL). That processing is documented in [`docs/guides/privacy.md`](../../docs/guides/privacy.md) and GDPR export/delete APIs.

## Customer checklist

1. Declare **App activity** / install referrer usage if you call `resolveDeferred` / Install Referrer.
2. Set **Data collection** = yes only for categories above that you actually enable.
3. Set **Data sharing** with Taqlyn as your deferred-linking service provider (processor) when using Taqlyn cloud resolve.
4. Do **not** claim the SDK collects Advertising ID unless you add ATT/AAID yourself outside this SDK.
5. Link your privacy policy; include Taqlyn subprocessors if using cloud ([`docs/guides/subprocessors.md`](../../docs/guides/subprocessors.md)).

## Related

- SDK README: [`README.md`](./README.md)
- Privacy guide: [`docs/guides/privacy.md`](../../docs/guides/privacy.md)
- Research: [`docs/research/compliance/privacy-and-store-policy.md`](../../docs/research/compliance/privacy-and-store-policy.md)
