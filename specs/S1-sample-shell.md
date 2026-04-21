# Spec S1 — sample-compose shell (nav host, theme, shared UI, session, RPC wiring)

## Goal
Replace the current single-screen `sample-compose` demo with a low-fi-themed navigation shell — hand-rolled `Route` + `Navigator`, grayscale + red-orange `WalletTheme`, Helvetica / SF Mono `Typography`, shared UI primitives (`PhoneFrame`, `PrimaryButton`, `ChainChip`, `BalanceRow`, `MonoText`), an in-memory `WalletSession` exposed via `CompositionLocal`, and an `RpcClientFactory` that hands out `wallet-rpc` clients for Ethereum and Base. This is the hot-file seam that S2–S6 all depend on: every subsequent flow phase plugs a new folder under `flows/` into the `when(route)` dispatch in `App.kt` and reads/writes the shared session.

## Module(s) touched
- `sample-compose` — only. Sources under `commonMain` and the module's `build.gradle.kts` dependency block.
- `wallet-core`, `wallet-evm`, `wallet-rpc`, `wallet-utils` — **not touched**. Existing public API is consumed as-is.
- `gradle/libs.versions.toml`, `settings.gradle.kts` — **not touched** (hot files per CLAUDE.md §7). No new library aliases, no Compose Multiplatform navigation dep.
- `sample-app` — **not touched**. Its `MainActivity` already wires `WalletSampleApp()`; after S1 it simply hosts the new NavHost.

## Files expected to change
- `sample-compose/build.gradle.kts` (modify)
  - Add `api(project(":wallet-rpc"))` and `api(project(":wallet-evm"))` to `commonMain.dependencies`.
  - Remove the `iosX64() / iosArm64() / iosSimulatorArm64()` target block and the associated framework export. `sample-compose` becomes Android-only for this spec set.
  - Do **not** touch `libs.versions.toml`; reuse existing compose / kotlin / ktor aliases only.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/App.kt` (modify)
  - Delete the existing `Card`-based demo body. Keep the entry point `@Composable fun WalletSampleApp()`.
  - New body: construct a `Navigator` (remembered), build a `WalletSession` (remembered), wrap in `WalletTheme { WalletSessionHolder.Provide(session) { ... } }`, and dispatch via `when (val route = navigator.current) { is Route.Welcome -> WelcomeScreen(navigator) ; else -> PlaceholderScreen(route) }`.
  - `WelcomeScreen` is defined **inside** `App.kt` for S1 — it's the only flow screen this phase owns. It shows the app title, a short tagline, a `PrimaryButton("Create Wallet")` → `navigator.push(Route.Create)`, and a secondary button "Import existing" → `navigator.push(Route.Import)`. Both targets land on a `PlaceholderScreen` until S2/S3 replace them.
  - `PlaceholderScreen(route)` is a single `PhoneFrame` containing `MonoText("TODO: ${route::class.simpleName}")` and a back button that calls `navigator.pop()`. This is the seam S2–S6 replace by adding their own `when` branch.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/nav/Route.kt` (new)
  - `sealed class Route` with: `object Welcome`, `object Create`, `object Import`, `object Home`, `data class Send(val chainId: Long)`, `data class TxStatus(val txHash: String, val chainId: Long)`.
  - No behavior, no KDoc beyond a one-liner on the sealed class.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/nav/Navigator.kt` (new)
  - Plain class `Navigator(initial: Route)`. Holds a private `MutableState<List<Route>>` as the stack and exposes:
    - `val current: Route` — top of stack (reads the `MutableState` so Compose recomposes).
    - `fun push(route: Route)`
    - `fun pop(): Boolean` — returns false if at root.
    - `fun replace(route: Route)` — resets the stack to a single entry. Used by S2/S3 when a flow completes and routes to `Home`.
  - Companion helper `@Composable fun rememberNavigator(initial: Route = Route.Welcome): Navigator` using `rememberSaveable` is acceptable but not required; `remember` is fine for S1.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/theme/WalletTheme.kt` (new)
  - `@Composable fun WalletTheme(content: @Composable () -> Unit)` wrapping `MaterialTheme` with a grayscale palette plus one red-orange accent (`#FF4A1C` or similar; pick one value and centralize it).
  - Exposes a small `object WalletColors` with named tokens (`background`, `surface`, `outline`, `textPrimary`, `textSecondary`, `accent`) that screens reference directly rather than going through `MaterialTheme.colorScheme`. This keeps the low-fi palette intentional.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/theme/Typography.kt` (new)
  - `val WalletTypography: Typography` with body / label styles using `FontFamily.SansSerif` as a Helvetica fallback and `FontFamily.Monospace` as an SF Mono fallback. No font resources are bundled in S1; the system defaults are acceptable since the Android device ships Roboto (Helvetica-ish) and Courier/Monospace.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/PhoneFrame.kt` (new)
  - Rounded-rect frame that all flow screens slot into. Fixed aspect ratio, border in `WalletColors.outline`, background `WalletColors.surface`. Exposes a `content: @Composable ColumnScope.() -> Unit` slot.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/PrimaryButton.kt` (new)
  - Filled button, accent background, white text, full-width by default. Accepts `text: String`, `onClick: () -> Unit`, `enabled: Boolean = true`.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/ChainChip.kt` (new)
  - Row of two chips: "Ethereum" and "Base". Takes `selected: SupportedChain` and `onSelect: (SupportedChain) -> Unit`. Reads chain list from `ChainRegistry.all()` but filters to the two S1 targets.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/BalanceRow.kt` (new)
  - `@Composable fun BalanceRow(chain: SupportedChain, weiHex: String?)` — renders chain display name, formatted balance via `EthFormat.weiHexToEth`, and ticker. `null` balance renders as `—`.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/MonoText.kt` (new)
  - Thin wrapper around `Text` forcing `FontFamily.Monospace` and the mono style from `WalletTypography`.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/state/WalletSession.kt` (new)
  - `class WalletSession` with:
    - `var wallet: Wallet?` (wrapped in `MutableState`, nullable until S2/S3 sets it).
    - `var selectedChain: SupportedChain` defaulting to `SupportedChain.Ethereum`.
    - `var lastTxHash: String?` defaulting to `null`.
  - Override `toString()` to `"WalletSession(wallet=${if (wallet == null) "null" else "REDACTED"}, chain=${selectedChain.id}, lastTxHash=$lastTxHash)"`. The `Wallet` reference holds the mnemonic; the session's own `toString` must never emit it (CLAUDE.md §4.1). The session does **not** log, does **not** persist, does **not** accept a logger.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/state/WalletSessionHolder.kt` (new)
  - `val LocalWalletSession = staticCompositionLocalOf<WalletSession> { error("WalletSession not provided") }`.
  - `object WalletSessionHolder { @Composable fun Provide(session: WalletSession, content: @Composable () -> Unit) = CompositionLocalProvider(LocalWalletSession provides session, content = content) }`.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/rpc/RpcClientFactory.kt` (new)
  - `object RpcClientFactory { fun forChain(chain: SupportedChain): RpcClient }`.
  - Hardcoded public endpoints: Ethereum → `https://ethereum.publicnode.com`, Base → `https://base.publicnode.com`. Throws `IllegalArgumentException` for any other `SupportedChain`.
  - Builds via `RpcClient.withDefaults(baseUrl)`. S5/S6 consume this; S1 only needs to compile and surface the factory.
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/format/EthFormat.kt` (new)
  - `fun weiHexToEth(weiHex: String, decimals: Int = 6): String` and `fun weiHexToGwei(weiHex: String): String`. Input is a `0x`-prefixed hex string (the shape `RpcClient.getBalance` returns). Uses `java.math.BigInteger` — acceptable because `sample-compose` is now Android/JVM-only.
  - Must not throw on malformed input; return `"—"` as fallback.

## Design

### Navigation model
- Hand-rolled to avoid adding `androidx.navigation:navigation-compose` or `compose.jetbrains.navigation` to `libs.versions.toml` (hot file, CLAUDE.md §7). The entire nav surface is ~40 LoC across `Route.kt` + `Navigator.kt`.
- `App.kt`'s `when (route)` is the only dispatch site. S2–S6 add one branch each; no other file changes shape.
- Back handling is explicit via `navigator.pop()` buttons inside screens. Android system back integration is out of scope for S1 (S4 can wire `BackHandler` once `Home` exists).

### Session model
- Single `WalletSession` instance per process, held by `remember { WalletSession() }` in `WalletSampleApp()` and provided through `LocalWalletSession`.
- No DI framework (CLAUDE.md §8). Screens read the session with `LocalWalletSession.current`.
- Session is **in-memory only**. No DataStore, no SharedPreferences, no file IO. A process restart drops the wallet — this is acceptable for a showcase and keeps mnemonic handling minimal.
- The `Wallet` type already lives in `wallet-core` and owns its own mnemonic redaction. `WalletSession.toString()` must not invoke `wallet?.toString()`; it prints the literal `"REDACTED"` when non-null (see Files above).

### RPC wiring
- `RpcClientFactory.forChain` returns a fresh `RpcClient` per call. Caching is not a goal for S1; S5 can add a per-chain cache if it matters for latency. Ktor's default engine is fine on Android.
- Endpoints are hardcoded. A future spec can move them into a config surface; do **not** add a config file for S1.

### Theme
- Grayscale: background `#F2F1EE` (off-white paper), surface `#FFFFFF`, outline `#1A1A1A`, textPrimary `#1A1A1A`, textSecondary `#6E6E6E`.
- Accent: `#FF4A1C` (red-orange). One accent color, used only for `PrimaryButton` and selection states.
- Typography leans on system families. Do not fetch fonts, do not add a `resources` dep.

### Crypto hygiene touchpoints
- No `kotlin.random.Random` anywhere in this module (CLAUDE.md §4.2). Entropy for wallet creation is fully owned by `wallet-core`'s `Wallet.createWithTrustWalletCore()`, which S1 does not call.
- No logging of mnemonic, private key, signing payload, or anything derived. `EthFormat` operates only on hex balance strings — safe. `println`, `Log.d`, and exception messages in S1 code must not contain any `wallet.mnemonic` access.
- Address display (once S4/S5 add it) is lowercased before equality but S1 does no equality on addresses.

### iOS targets
- Dropped from `sample-compose/build.gradle.kts`. Rationale: (a) `wallet-evm` and `wallet-rpc` have no iOS targets per CLAUDE.md §1, so adding them is out of scope for a sample-shell phase; (b) a working iOS showcase requires installing a `TrustWalletCoreIosAdapter` from the host, which is its own spec set.
- This leaves `sample-compose/src/iosMain/MainViewController.kt` and `sample-compose/src/iosMain/MockTrustWalletCoreAdapter.kt` orphaned on disk. They are not referenced by any remaining target and are left for a dedicated cleanup spec — do not delete them in S1.

## Acceptance criteria
A task is **not done** until every one of these passes.
1. `./gradlew :sample-compose:compileKotlinJvm` — green. (There is no `compileKotlinIos*` to run because iOS targets were dropped.)
2. `./gradlew :sample-app:assembleDebug` — green. This is the effective end-to-end gate for the Android showcase.
3. `./gradlew :sample-compose:compileDebugKotlinAndroid` (or the equivalent `:sample-compose:assemble`) — green. Confirms the Android source set still typechecks after the dep additions.
4. `App.kt` no longer contains any call to `Wallet.createWithTrustWalletCore()`, `ChainRegistry.all().joinToString(...)`, or any `Button`/`OutlinedButton` from `androidx.compose.material3` wired to wallet creation. That logic belongs to S2.
5. `App.kt`'s initial route is `Route.Welcome`. The Welcome screen exposes exactly two buttons, navigating to `Route.Create` and `Route.Import` respectively. Both land on the generic `PlaceholderScreen` until S2/S3 ship.
6. Every file listed in **Files expected to change** exists at the stated path with the stated package (`xyz.wallet.toolkit.sample[.<subpkg>]`). No flow folders (`flows/create/`, `flows/import/`, `flows/home/`, `flows/send/`, `flows/tx/`) exist after S1.
7. `WalletSession.toString()` does not contain the substring `mnemonic` at runtime, and does not call `wallet.toString()`. Stated as a design invariant because `sample-compose` has no `commonTest` source set in its `build.gradle.kts` today and S1 does not add one (that would touch test-runtime aliases, a hot-file concern). The invariant is enforced by code review: `toString()` must read as shown in the Files section.
8. `sample-compose/build.gradle.kts` no longer declares `iosX64()`, `iosArm64()`, or `iosSimulatorArm64()`, and no longer exports a `SampleCompose` framework.
9. `gradle/libs.versions.toml` and `settings.gradle.kts` are byte-identical to their state before S1.
10. No direct imports of `java.util.Random` or `kotlin.random.Random` appear in any file under `sample-compose/src/commonMain/`.

## Non-goals
- **Create flow** — S2 owns `flows/create/` and the `Route.Create` branch in `App.kt`.
- **Import flow** — S3 owns `flows/import/` and the `Route.Import` branch.
- **Home / portfolio screen** — S4 owns `flows/home/`, `Route.Home`, and wiring `RpcClientFactory.forChain(session.selectedChain).getBalance(...)` into `BalanceRow`.
- **Send flow** — S5 owns `flows/send/`, signing via `wallet-evm`, and `RpcClient.sendRawTransaction`. S1 only exposes the factory.
- **Tx-status flow** — S6 owns `flows/tx/` and polling `RpcClient.getTransactionReceipt`.
- **iOS parity for `sample-compose`** — future spec set. This includes restoring the iOS targets, writing a `TrustWalletCoreIosAdapter`, and resurrecting `MainViewController.kt`.
- **Navigation library** — explicitly avoided. No Compose Multiplatform navigation dep is added; nav is hand-rolled. Reopening this requires touching `libs.versions.toml` (hot file).
- **Cleanup of orphaned iOS sample-compose files** (`MainViewController.kt`, `MockTrustWalletCoreAdapter.kt`) — future cleanup spec. They compile nowhere after S1 but are harmless on disk.
- **Any changes to `wallet-core`, `wallet-evm`, `wallet-rpc`, `wallet-utils`** — forbidden in this spec.
- **Persistence** — no DataStore/SharedPreferences/file IO for the session. The wallet is lost on process death; that's intentional.
- **Font bundling** — no `.ttf`/`.otf` resources. System families only.
- **DI framework** — no Koin/Hilt/manual service locator. `CompositionLocal` + constructor injection only (CLAUDE.md §8).
- **Tests** — `sample-compose` has no `commonTest` source set today. S1 does not add one; the `WalletSession.toString()` invariant is enforced by review, not test.

## Why this is interesting for the experiment
- **Pure seam, maximum downstream blast radius.** Every one of S2–S6 depends on this file set and on nothing else. Measuring tokens/rework here sets the floor for how cheap a "scaffolding" phase can be in the parallel-agent setup.
- **Hot-file discipline under pressure.** The obvious instinct is to reach for `androidx.navigation:navigation-compose` — which would touch `libs.versions.toml`. The spec forces the hand-rolled alternative, which is a good probe of whether the implementer agent respects CLAUDE.md §7.
- **Crypto-hygiene probe without crypto code.** `WalletSession.toString()` is a soft target: it's a data class next to a `Wallet` that holds a mnemonic. An agent that auto-generates `data class` here will leak. The spec explicitly requires a non-data class with a hand-written `toString()`, and the review will catch the default-`toString` mistake.
- **Dropping iOS is a judgment call.** A naive read of the handoff README suggests iOS parity is a goal. CLAUDE.md §1 says otherwise for `wallet-evm` / `wallet-rpc`. S1 forces the honest trade-off: showcase ships Android-only, iOS is a separate spec set. How the agent handles the orphaned `iosMain/` files is a signal.
- **Parallelization baseline.** S2–S6 are file-disjoint from each other once S1 lands. This phase's outputs define the interface contract for all five downstream phases running concurrently.
