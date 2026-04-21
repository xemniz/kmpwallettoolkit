# Plan — S1 sample-compose shell

## 1. Summary
S1 replaces the current single-screen `sample-compose` demo with a low-fi themed navigation shell: hand-rolled `Route` + `Navigator`, a grayscale + red-orange `WalletTheme`, shared UI primitives, an in-memory `WalletSession` exposed via `CompositionLocal`, and an `RpcClientFactory` that hands out `wallet-rpc` clients for Ethereum and Base. The spec's approach is reasonable: it's a pure scaffolding phase with maximum downstream blast radius (S2–S6 all plug into the `when(route)` dispatch), and the hand-rolled nav avoids touching `libs.versions.toml` (CLAUDE.md §7 hot file). iOS targets are intentionally dropped from `sample-compose`.

## 2. Files to create / modify

All source set: `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/...` (Android-only module after this spec — no iOS, no jvm, no androidMain split; `commonMain` is effectively the Android source set here).

Build script (module-level, not shared):
- **modify** `sample-compose/build.gradle.kts` — drop `iosX64/iosArm64/iosSimulatorArm64` target block and `SampleCompose` framework export; add `api(project(":wallet-rpc"))` and `api(project(":wallet-evm"))` to `commonMain.dependencies`.

New files (all `commonMain`):
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/nav/Route.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/nav/Navigator.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/theme/WalletTheme.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/theme/Typography.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/PhoneFrame.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/PrimaryButton.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/ChainChip.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/BalanceRow.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/ui/MonoText.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/state/WalletSession.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/state/WalletSessionHolder.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/rpc/RpcClientFactory.kt`
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/format/EthFormat.kt`

Rewritten:
- `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/App.kt` — replaces demo body; keeps `WalletSampleApp()` entry point. Hosts `WelcomeScreen` (in-file) and `PlaceholderScreen`.

Left orphaned on disk (do NOT delete — out-of-scope):
- `sample-compose/src/iosMain/kotlin/xyz/wallet/toolkit/sample/MainViewController.kt`
- `sample-compose/src/iosMain/kotlin/xyz/wallet/toolkit/sample/MockTrustWalletCoreAdapter.kt`

**Hot files — not touched**: `gradle/libs.versions.toml`, `settings.gradle.kts`, `wallet-core/.../ChainRegistry.kt`, `wallet-core/.../Chain.kt`, `CLAUDE.md`, `.claude/agents/*`, `gradle.properties`, `gradle-daemon-jvm.properties`. `sample-app/build.gradle.kts` is also left untouched per spec.

## 3. Phases

This module has no test source set; the done-gate is compilation, not tests (CLAUDE.md §3 — `allTests` doesn't apply here).

### Phase 1 — Module build surface
1. Edit `sample-compose/build.gradle.kts`:
   - Delete the `listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { ... }` block and its `framework { ... export(...) }` contents.
   - Under `sourceSets { commonMain.dependencies { ... } }` add `api(project(":wallet-rpc"))` and `api(project(":wallet-evm"))`.
2. Temporarily neuter `App.kt` (stub body referencing only the retained deps) so the module still compiles.
3. Gate: `./gradlew :sample-compose:compileKotlinJvm` — green.

### Phase 2 — Nav + theme + session skeleton
1. Create `nav/Route.kt`:
   - `sealed class Route` with `object Welcome`, `object Create`, `object Import`, `object Home`, `data class Send(val chainId: Long)`, `data class TxStatus(val txHash: String, val chainId: Long)`.
2. Create `nav/Navigator.kt`:
   - `class Navigator(initial: Route)` with private `mutableStateOf<List<Route>>(listOf(initial))`.
   - `val current: Route get() = stack.last()` (backed by `MutableState` so Compose recomposes).
   - `fun push(route: Route)`, `fun pop(): Boolean` (returns false at root), `fun replace(route: Route)`.
   - `@Composable fun rememberNavigator(initial: Route = Route.Welcome): Navigator` using `remember`.
3. Create `theme/WalletTheme.kt`:
   - `object WalletColors` with `background = Color(0xFFF2F1EE)`, `surface = Color(0xFFFFFFFF)`, `outline = Color(0xFF1A1A1A)`, `textPrimary = Color(0xFF1A1A1A)`, `textSecondary = Color(0xFF6E6E6E)`, `accent = Color(0xFFFF4A1C)`.
   - `@Composable fun WalletTheme(content: @Composable () -> Unit)` wrapping `MaterialTheme` with a lightColorScheme derived from `WalletColors`.
4. Create `theme/Typography.kt`:
   - `val WalletTypography: Typography` — body/label/title using `FontFamily.SansSerif`; pin a `val MonoTextStyle: TextStyle` using `FontFamily.Monospace` for reuse by `MonoText`.
5. Create `state/WalletSession.kt`:
   - `class WalletSession` (NOT a `data class`).
   - Backing state via `mutableStateOf<Wallet?>(null)`, `mutableStateOf(SupportedChain.Ethereum)`, `mutableStateOf<String?>(null)`.
   - Public `var wallet: Wallet?`, `var selectedChain: SupportedChain`, `var lastTxHash: String?` delegating to the above.
   - Hand-rolled `override fun toString(): String = "WalletSession(wallet=${if (wallet == null) "null" else "REDACTED"}, chain=${selectedChain.id}, lastTxHash=$lastTxHash)"`. Must NOT call `wallet?.toString()` and must NOT contain the substring `mnemonic`.
6. Create `state/WalletSessionHolder.kt`:
   - `val LocalWalletSession = staticCompositionLocalOf<WalletSession> { error("WalletSession not provided") }`.
   - `object WalletSessionHolder { @Composable fun Provide(session: WalletSession, content: @Composable () -> Unit) = CompositionLocalProvider(LocalWalletSession provides session, content = content) }`.
7. Gate: `./gradlew :sample-compose:compileKotlinJvm` — green.

### Phase 3 — UI primitives
1. `ui/MonoText.kt` — `@Composable fun MonoText(text: String, modifier: Modifier = Modifier)` applying `FontFamily.Monospace` via `MonoTextStyle`.
2. `ui/PhoneFrame.kt` — rounded-rect Box/Column with fixed aspect ratio, `WalletColors.outline` border, `WalletColors.surface` background, exposes `content: @Composable ColumnScope.() -> Unit`.
3. `ui/PrimaryButton.kt` — full-width `Button` using accent background, white text, `text: String`, `onClick: () -> Unit`, `enabled: Boolean = true`.
4. `ui/ChainChip.kt` — Row of two chips for Ethereum + Base, filtered from `ChainRegistry.all()`; signature `(selected: SupportedChain, onSelect: (SupportedChain) -> Unit)`.
5. `ui/BalanceRow.kt` — `@Composable fun BalanceRow(chain: SupportedChain, weiHex: String?)`; renders `chain.displayName`, formatted balance via `EthFormat.weiHexToEthDecimal`, ticker. `null` renders `—`.
6. Gate: `./gradlew :sample-compose:compileKotlinJvm` — green.

### Phase 4 — RPC factory + formatting
1. `rpc/RpcClientFactory.kt`:
   - `object RpcClientFactory { fun forChain(chain: SupportedChain): RpcClient }`.
   - Ethereum → `https://ethereum-rpc.publicnode.com`.
   - Base → `https://base-rpc.publicnode.com`.
   - Any other chain → `throw IllegalArgumentException("Unsupported chain: ${chain.id}")`.
   - Constructs via `RpcClient.withDefaults(baseUrl)`.
2. `format/EthFormat.kt` — pinned signatures (these are load-bearing for S4/S5):
   - `fun weiHexToEthDecimal(hex: String): String` — hex `0x`-prefixed wei → decimal string in ETH (18 decimals). Returns `"—"` on malformed input; never throws.
   - `fun ethDecimalToWei(decimal: String): String` — ETH decimal string → wei as decimal string (no `0x` prefix). Throws `IllegalArgumentException` on malformed input (it's a user-input parse, not a render).
   - `fun gweiToWei(gwei: String): String` — gwei decimal string → wei decimal string. Same error contract as `ethDecimalToWei`.
   - Implementation uses `java.math.BigInteger` / `java.math.BigDecimal`. Acceptable: `sample-compose` is Android-only after this spec.
3. Gate: `./gradlew :sample-compose:compileKotlinJvm` — green.

### Phase 5 — App.kt rewrite
1. Replace `App.kt` body:
   - `@Composable fun WalletSampleApp()`:
     - `val navigator = rememberNavigator()`
     - `val session = remember { WalletSession() }`
     - `WalletTheme { WalletSessionHolder.Provide(session) { when (val route = navigator.current) { is Route.Welcome -> WelcomeScreen(navigator); else -> PlaceholderScreen(route, navigator) } } }`.
   - `@Composable private fun WelcomeScreen(navigator: Navigator)`:
     - `PhoneFrame { ... }` containing title, tagline, `PrimaryButton("Create Wallet") { navigator.push(Route.Create) }`, secondary outlined button `"Import existing" { navigator.push(Route.Import) }`.
   - `@Composable private fun PlaceholderScreen(route: Route, navigator: Navigator)`:
     - `PhoneFrame { MonoText("TODO: ${route::class.simpleName}"); Button(onClick = { navigator.pop() }) { Text("Back") } }`.
2. Verify `App.kt` contains no reference to `Wallet.createWithTrustWalletCore`, `ChainRegistry.all().joinToString`, or creation-flow buttons.
3. Gate: `./gradlew :sample-compose:compileKotlinJvm` — green.

### Phase 6 — End-to-end compile gate
1. `./gradlew :sample-compose:compileKotlinJvm` — green.
2. `./gradlew :sample-compose:compileDebugKotlinAndroid` — green (verifies Android source-set typechecks after dep additions).
3. `./gradlew :sample-app:assembleDebug` — green (effective end-to-end gate).
4. Grep-audit (manual, by the implementer):
   - No `kotlin.random.Random` or `java.util.Random` imports under `sample-compose/src/commonMain/`.
   - No `println`/`Log.*`/exception messages referencing `wallet.mnemonic`, private keys, or signing payloads.
   - `WalletSession.toString()` body contains literal `"REDACTED"` and does NOT contain the substring `mnemonic`.
   - `App.kt` contains exactly one `when (val route = navigator.current)` dispatch with branches for `Route.Welcome` and an `else -> PlaceholderScreen(...)`.

## 4. Golden vectors
Not applicable. S1 does no signing, no key derivation, no serialization of signing input. `EthFormat` is decimal ↔ wei conversion with a stable, published constant (10^18); downstream specs that do sign must carry their own golden vectors.

One sanity pin for `EthFormat` (not a golden vector, just a reference point the implementer should manually confirm works):
- `weiHexToEthDecimal("0xde0b6b3a7640000")` → `"1"` (exactly 10^18 wei = 1 ETH).
- `ethDecimalToWei("1")` → `"1000000000000000000"`.
- `gweiToWei("1")` → `"1000000000"`.

## 5. Risks / Open questions

1. **Endpoint drift between spec and operator note.** The spec (line 58) names `https://ethereum.publicnode.com` / `https://base.publicnode.com`; the operator note pins `https://ethereum-rpc.publicnode.com` / `https://base-rpc.publicnode.com`. The plan uses the operator-pinned URLs because downstream specs will rely on them. Flagging in case the spec must be updated to match.
2. **`EthFormat` API drift between spec and operator note.** The spec names `weiHexToEth(weiHex: String, decimals: Int = 6): String` and `weiHexToGwei(weiHex: String): String`. The operator pins three functions with different names: `weiHexToEthDecimal`, `ethDecimalToWei`, `gweiToWei` — chosen to satisfy S4/S5. The plan uses the operator-pinned signatures (they're a strict superset for downstream needs), but `BalanceRow` will call `weiHexToEthDecimal` rather than `weiHexToEth`. Flagging so the spec can be updated.
3. **Orphaned iOS files.** `sample-compose/src/iosMain/kotlin/xyz/wallet/toolkit/sample/MainViewController.kt` and `MockTrustWalletCoreAdapter.kt` remain on disk after dropping iOS targets. They compile in no target. This is intentional per the spec (non-goal 6) but is a code-hygiene smell; a future cleanup spec should delete them.
4. **No `commonTest` source set.** `WalletSession.toString()` redaction invariant and `EthFormat` correctness are enforced by review only. If the implementer agent adds tests, they must not touch `libs.versions.toml` test aliases.
5. **Android `BackHandler`.** System-back integration is explicitly deferred to S4. For S1 the only way out of `PlaceholderScreen` is the in-screen back button.
6. **`rememberSaveable` for `Navigator`.** The spec allows but does not require it. The plan uses plain `remember`; process death drops the nav stack along with the session. Acceptable for a showcase.

## 6. What this plan does NOT do
- Does not add any library, alias, or version to `gradle/libs.versions.toml`.
- Does not edit `settings.gradle.kts`, `gradle.properties`, `gradle-daemon-jvm.properties`, or any `CLAUDE.md`.
- Does not touch `wallet-core`, `wallet-evm`, `wallet-rpc`, or `wallet-utils` source.
- Does not edit `sample-app/build.gradle.kts` or `sample-app/src/**`.
- Does not add Compose Multiplatform navigation, Voyager, Decompose, or any nav library.
- Does not add Koin, Hilt, or any DI framework.
- Does not implement the Create flow (S2), Import flow (S3), Home flow (S4), Send flow (S5), or Tx-status flow (S6). `Route.Create`, `Route.Import`, `Route.Home`, `Route.Send`, `Route.TxStatus` all land on `PlaceholderScreen`.
- Does not create `flows/create/`, `flows/import/`, `flows/home/`, `flows/send/`, or `flows/tx/` directories.
- Does not delete the orphaned `sample-compose/src/iosMain/*.kt` files.
- Does not restore iOS targets, write a `TrustWalletCoreIosAdapter`, or resurrect `MainViewController.kt`.
- Does not bundle `.ttf`/`.otf` font resources.
- Does not persist `WalletSession` (no DataStore, no SharedPreferences, no file IO).
- Does not cache `RpcClient` instances inside `RpcClientFactory`.
- Does not add `ktlintCheck` or `detekt` to any gate (not configured in this repo).
- Does not run `./gradlew build` or any full-repo task (CLAUDE.md §8).
