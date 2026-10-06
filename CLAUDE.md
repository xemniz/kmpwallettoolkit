# kmp-wallet-toolkit — Agent Conventions

This file is read by every Claude Code agent that operates on this repo. It encodes rules that are **not** inferable from the code alone. Read it before writing anything.

The repo is a KMP crypto wallet library. Code that "looks right" can still leak keys, drift from golden vectors, or silently weaken security properties. Err verbose and err cautious.

---

## 1. Module layout and dependency graph

```
wallet-utils   (no deps — pure commonMain)
      ↑
wallet-core    (expect/actual seam — the only module with platform-specific source sets)
   ↑       ↑
wallet-evm  wallet-rpc
```

| Module       | Source sets                                       | Package root                     | Purpose |
|--------------|---------------------------------------------------|----------------------------------|---------|
| wallet-utils | commonMain, jvmMain, androidMain, iosMain         | `xyz.wallet.toolkit.utils`       | Hex & encoding helpers |
| wallet-core  | commonMain, commonTest, jvmMain, androidMain, iosMain | `xyz.wallet.toolkit.core`    | Wallet facade, chain registry, signer boundary, Trust Wallet Core bridge |
| wallet-evm   | commonMain, commonTest (JVM, Android, iOS)       | `xyz.wallet.toolkit.evm`         | EVM transaction data + signing payload serialization |
| wallet-rpc   | commonMain, commonTest (JVM, Android, iOS)       | `xyz.wallet.toolkit.rpc`         | JSON-RPC client (Ktor) |

All four toolkit modules target JVM, Android, and iOS. Platform-specific native calls remain confined to wallet-core.

---

## 2. commonMain / androidMain / iosMain / jvmMain rules

### Goes in commonMain
- Pure Kotlin. `kotlin.*`, `kotlinx.*`, `kotlinx.serialization.*`, `io.ktor.*` (multiplatform artifacts only), `okio.*`.
- Domain types: transactions, addresses, chain identifiers.
- Interfaces and `expect` declarations.
- Tests that run on every platform.

### Must NOT appear in commonMain
- `java.*`, `javax.*`, `android.*`, `androidx.*` — these break iOS compilation.
- `kotlin.random.Random` for any security-sensitive material. See §4.
- `System.currentTimeMillis()`, `System.getenv`, `Thread.*` — use `kotlinx.datetime` / multiplatform equivalents.
- JNI references (`wallet.core.jni.*`) — those live in `androidMain` only.
- Objective-C / cinterop references — those live in `iosMain` only.

### androidMain specifics
- Trust Wallet Core JNI: `wallet.core.jni.HDWallet`, `wallet.core.jni.CoinType`, `wallet.core.jni.AnySigner`. Access via the reflection bridge in `wallet-core/androidMain/.../TrustWalletCoreNativeBridge.android.kt` — do not add direct imports elsewhere.
- `java.security.SecureRandom` is allowed here (and required for key material, see §4).
- `android.security.keystore.*` is allowed here.

### iosMain specifics
- No JNI. The bundled Trust Wallet Core bridge calls the WalletCore C API through cinterop. Custom backends implement `WalletEngine` and are injected with `WalletKit.withEngine(...)`.
- For randomness on iOS: use a cryptographically secure platform source or Trust Wallet Core; do **not** use `kotlin.random.Random`.

### jvmMain specifics
- Currently stubs throwing `NotImplementedError` for Trust Wallet Core calls — used only for unit-test compilation. Don't add production logic here unless the task explicitly calls for it.

### expect/actual discipline
- `expect` declarations live in `commonMain`. Every target that compiles the common source set **must** have a matching `actual`.
- If you add an `expect fun`/`expect class`, provide actuals for every configured target. The toolkit currently uses `jvmMain`, `androidMain`, and `iosMain` (with native C API code in `iosWalletCoreMain`).
- Never use `expect`/`actual` to paper over a design that should be an interface + DI. If the platform difference is "a different implementation of the same contract", use an interface and inject it.
- Do not rename or remove `expect` symbols without updating every actual in the same change.

---

## 3. Build & test commands

**Never run `./gradlew build`.** It compiles everything and is slow (several minutes). Always scope to the module(s) you changed.

### Per-module test commands (these are the "done" gates)

| Module       | Verify command |
|--------------|----------------|
| wallet-utils | `./gradlew :wallet-utils:allTests` |
| wallet-core  | `./gradlew :wallet-core:allTests` |
| wallet-evm   | `./gradlew :wallet-evm:allTests` |
| wallet-rpc   | `./gradlew :wallet-rpc:allTests` |

`allTests` aggregates `jvmTest` plus `iosX64Test` / `iosSimulatorArm64Test` for modules with iOS targets. Android tests are explicitly enabled through the AGP KMP test builders. Native wallet-core signing vectors run in `androidDeviceTest`; execute `:wallet-core:connectedAndroidDeviceTest` on an emulator/device as well as `allTests`. iOS x64 runtime tests may be skipped on an ARM host, so verify simulator ARM64 tests actually execute.

### Compile-only check (fast smoke)

```
./gradlew :<module>:compileKotlinJvm
```

Use this when you want to confirm KMP source set typing without spinning up Android or iOS test VMs.

### When you changed wallet-core

You changed the expect/actual seam. Also run:

```
./gradlew :wallet-core:compileKotlinIosX64 :wallet-core:compileKotlinJvm
```

This is the fastest way to catch "I accidentally used `java.security` in commonMain" — iOS compilation will fail.

### There is no ktlint / detekt configured in this repo.
Do **not** add `ktlintCheck` to your done-gate; it will fail with "task not found". If a spec lists a lint command, treat it as aspirational and note its absence.

---

## 4. Crypto hygiene — non-negotiable

These rules are stricter than most codebases because crypto code fails silently.

1. **Never log or toString sensitive material.** Mnemonics, private keys, seed bytes, signing payloads that contain a key, and anything derived from them must not appear in `toString()`, `println`, logger calls, or exception messages. If a data class holds any of the above, override `toString()` to return a redacted form (e.g. `"PrivateKey(redacted)"`).
2. **SecureRandom, never Random.** Any byte array destined for use as entropy, nonce, salt, IV, or key material must come from a cryptographically secure source. In commonMain, expose an `expect fun secureRandomBytes(size: Int): ByteArray` and implement with `SecureRandom` on JVM/Android and `SecRandomCopyBytes` (via cinterop or adapter) on iOS. `kotlin.random.Random` is **never** acceptable for this purpose.
3. **Constant-time comparisons for secrets.** Comparing two byte arrays that represent an HMAC, MAC, or any secret-derived value must use a constant-time function — loop over every byte and OR the XOR result. Never use `contentEquals` or `==` on secret material.
4. **No `Random.nextLong()` for nonces, request IDs tied to signing, or transaction nonces.** Transaction nonces come from the RPC (`eth_getTransactionCount`), not from a PRNG.
5. **Golden vectors are the only acceptable signing test.** A signing test that asserts "output is 65 bytes" or "doesn't throw" is worthless. Every signing path must have at least one test that asserts the exact hex output of a known (mnemonic, tx) → signature triple from a trusted source (e.g. EIP-155 examples, Trezor test vectors, viem/ethers reference outputs). If you cannot produce such a vector, pause and ask; do **not** weaken the assertion.
6. **Never weaken an existing assertion to make a test pass.** If a test breaks, the *code* is wrong, not the test. If you believe the test was wrong, say so in the PR description and get explicit confirmation before changing it.
7. **Don't compare addresses case-sensitively.** EVM addresses are hex; checksum casing (EIP-55) is informational. Normalize to lowercase before equality checks, or preserve and validate the checksum — pick one and be consistent.
8. **Serialization of transactions is security-relevant.** `EvmTransaction` → signing payload JSON is a signing input. A change to field ordering, BigInteger-vs-string encoding, or optional-field defaults can change the signed hash. Any change to `EvmSigningPayload.kt` or adjacent code requires a golden-vector test.

---

## 5. Naming and style

- Package root is always `xyz.wallet.toolkit.<module-suffix>`. Do not introduce other roots.
- Kotlin source layout: `src/<sourceSet>/kotlin/xyz/wallet/toolkit/<module-suffix>/`.
- Data classes for plain payloads, `interface` + factory for anything with behavior and a platform actual.
- No `var` on public API. Prefer immutable `val` + `copy()`.
- No nullable public-API parameters unless the optionality is a domain fact (e.g. `dataHex: String? = null` on `EvmTransaction`).
- Follow the existing indentation (4 spaces) and trailing comma style (present on multi-line param lists).
- Do not add KDoc for self-explanatory types. Add KDoc when the invariant is non-obvious (see `EvmSigningPayload.kt` for the canonical style).

---

## 6. Dependency rules

- All deps go through `gradle/libs.versions.toml`. Never hardcode a coordinate in a `build.gradle.kts`.
- If you add a new library, add the version in `[versions]`, the coordinate in `[libraries]`, and reference it via `libs.<alias>` in the module. This file is a coordination hazard — see §7.
- Do not pull in a new crypto library without raising it explicitly. The project's stance is "delegate to Trust Wallet Core for primitives"; introducing secp256k1-kmp, bouncycastle, libsodium, etc. is a design change, not an implementation detail.
- Do not widen a module's dependency surface without reason. wallet-utils has one dep (okio) — keep it that way.

---

## 7. Multi-agent coordination

Parallel agents run in separate worktrees. Files that are touched by more than one agent will cause merge conflicts or, worse, a silent race. Treat these as shared resources:

- `gradle/libs.versions.toml` — **hot file**. If your task needs a new dep, add it in a dedicated commit and announce it before other agents start.
- `settings.gradle.kts` — same.
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/ChainRegistry.kt` and `Chain.kt` — any "new chain" task touches these; serialize such tasks.
- `CLAUDE.md`, `.claude/agents/*` — never modified by an implementer agent.

If you find yourself needing to edit a hot file, stop and raise it to the user or the planner agent.

---

## 8. What NOT to do

- Do **not** run `./gradlew build`, `./gradlew clean build`, or any task that invokes every module. Scope to the module.
- Do **not** edit files outside the modules named in your spec's "module(s) touched" field.
- Do **not** add sample-app, sample-compose, or iosApp usage of a new API as part of an implementation task — those are downstream integration work.
- Do **not** introduce Koin, Hilt, or any DI framework in the toolkit modules (`wallet-utils`, `wallet-core`, `wallet-evm`, `wallet-rpc`). These are library surface — consumers pick their own DI. Constructor injection only here.
  - DI **is** allowed in the sample/showcase modules (`sample-compose`, `sample-app`, `iosApp`). They use Koin 4.x + AndroidX Lifecycle ViewModel KMP. New screen VMs / shared repos go in `sample-compose/.../di/AppModule.kt`; init via `initKoin { ... }` (Android: `WalletSampleApplication.onCreate`; iOS: `iOSApp.init()` calling `KoinInitKt.doInitKoinIos`).
- Do **not** silence a compile warning with `@Suppress` without a one-line justification in the PR description.
- Do **not** edit `gradle-daemon-jvm.properties` or `gradle.properties` without explicit instruction — this is a Gradle daemon stability hazard across worktrees.

---

## 9. When in doubt

Ask. For crypto code especially, the cost of pausing is zero and the cost of a wrong commit is a CVE. Prefer writing a failing test that demonstrates the ambiguity over guessing.
