# I — Session persistence — remember the wallet across app restarts

## Goal

Persist the active wallet's mnemonic on-device using the platform's
standard secret store (Android Keystore–backed `EncryptedSharedPreferences`;
iOS Keychain via the runtime-adapter pattern already used for Trust Wallet
Core). On app start:

- If a mnemonic is persisted → hydrate a `WalletSession` from it and land
  on `Route.Home`, skipping `Welcome`.
- If no mnemonic → behave exactly as today (land on `Welcome`).

Add a **Sign out** action on `Home` that clears the secret store and
returns the user to `Welcome`.

This is a showcase-level feature: it lives in the sample modules, not in
the `wallet-*` library. No new library API surface.

---

## Module(s) touched

- `sample-compose` (commonMain)
- `sample-app` (Android JVM sources + `build.gradle.kts`)
- `iosApp` (Swift sources only)
- `gradle/libs.versions.toml` **(hot file)** — add `androidx-security-crypto`
- `sample-app/build.gradle.kts` — add the dep (adjacent to the hot file)

No other file is modified. `wallet-*` modules are **not** touched.

---

## Files expected to change

| File | Role |
|------|------|
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/state/SecureWalletStorage.kt` **(new)** | Interface: `suspend fun save(mnemonic: String)`, `suspend fun load(): String?`, `suspend fun clear()`. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/state/SecureWalletStorageRuntime.kt` **(new)** | `object` with `install(storage: SecureWalletStorage)`, `get(): SecureWalletStorage`, plus a `NoOp` fallback used if no host installs one (in-memory only; logs no secrets). |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/App.kt` | On first composition, `LaunchedEffect(Unit)` tries `SecureWalletStorageRuntime.get().load()`. If non-null, instantiate `Wallet.fromMnemonicWithTrustWalletCore(...)`, set on `WalletSession`, and `navigator.replace(Route.Home)`. Must not block the UI. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/create/CreateWalletScreen.kt` | After the user confirms the backup, call `SecureWalletStorageRuntime.get().save(mnemonic)` before `navigator.push(Route.Home)`. If save fails, surface a non-fatal warning but continue (wallet still exists in memory). |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/import/ImportWalletScreen.kt` | Same as Create — save on successful import before navigating Home. |
| `sample-compose/src/commonMain/kotlin/xyz/wallet/toolkit/sample/flows/home/AddressHeader.kt` | Add a "Sign out" icon button (text link if icon adds dep weight) to the right side of the header row. Tap → confirm dialog ("Sign out? This will remove the wallet from this device.") → on confirm: `clear()`, `session.wallet = null`, `navigator.replace(Route.Welcome)`. |
| `sample-app/src/main/kotlin/xyz/wallet/toolkit/sample/app/AndroidSecureWalletStorage.kt` **(new)** | Concrete impl backed by `EncryptedSharedPreferences` with `MasterKey.Builder(context).setKeyScheme(AES256_GCM).build()`. Store under prefs file `"wallet-secure"`, key `"mnemonic"`. All three suspend methods run on `Dispatchers.IO`. |
| `sample-app/src/main/kotlin/xyz/wallet/toolkit/sample/app/MainActivity.kt` | In `onCreate` **before** `setContent`, call `SecureWalletStorageRuntime.install(AndroidSecureWalletStorage(applicationContext))`. |
| `sample-app/build.gradle.kts` | Add `implementation(libs.androidx.security.crypto)`. |
| `gradle/libs.versions.toml` | Add `androidxSecurityCrypto = "1.1.0-alpha06"` under `[versions]` and `androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "androidxSecurityCrypto" }` under `[libraries]`. |
| `iosApp/iosApp/KeychainSecureWalletStorage.swift` **(new)** | Swift class conforming to `SampleComposeSecureWalletStorage` (the bridge-generated protocol). Uses `SecItemAdd/Copy/Delete` with service `"xyz.wallet.toolkit.sample"`, account `"mnemonic"`, and `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. |
| `iosApp/iosApp/iOSApp.swift` | Call `SecureWalletStorageRuntime.shared.install(storage: KeychainSecureWalletStorage())` in `init()`, alongside the existing `TrustWalletCoreRuntime` install. |

---

## Design

### The seam

`SecureWalletStorage` is a `commonMain` interface with three suspend
methods. Consumers in commonMain (App.kt, flow screens, the Home sign-out)
call `SecureWalletStorageRuntime.get()` to obtain the installed instance.

`SecureWalletStorageRuntime` holds a single nullable `SecureWalletStorage`.
`install(...)` sets it. `get()` returns the installed instance, or a
`NoOpSecureWalletStorage` fallback that keeps a transient in-memory value
(so tests and dev runs without wiring don't crash — but persistence is
lost on process death). The fallback must not log, print, or `toString`
the mnemonic (CLAUDE.md §4.1).

The install call happens once per process, before any `@Composable`
reads `get()`. On Android this is `MainActivity.onCreate`. On iOS this is
`iOSApp.init()`.

### Android implementation

```kotlin
class AndroidSecureWalletStorage(
    private val context: Context,
) : SecureWalletStorage {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "wallet-secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun save(mnemonic: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString("mnemonic", mnemonic).commit()
        Unit
    }

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        prefs.getString("mnemonic", null)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove("mnemonic").commit()
        Unit
    }
}
```

Use `.commit()` (synchronous on the IO dispatcher) rather than `.apply()`
so a subsequent `load()` on the same dispatcher sees the write. No
exceptions are swallowed — `EncryptedSharedPreferences` can throw on
keystore misuse and those must surface as a failed save (returned to the
caller via the suspend function throwing).

### iOS implementation

The adapter protocol is regenerated from the Kotlin interface via the
KMP → Obj-C bridge. Swift conforms and performs Keychain calls. All
queries use the same fixed `service` + `account` pair so add/copy/delete
target the same item. Accessibility attribute is
`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` — i.e. the item is
readable after the first unlock following boot, and not synced to
iCloud Keychain.

```swift
class KeychainSecureWalletStorage: SecureWalletStorage {
    private let service = "xyz.wallet.toolkit.sample"
    private let account = "mnemonic"

    func save(mnemonic: String) async throws { ... }
    func load() async throws -> String? { ... }
    func clear() async throws { ... }
}
```

The Kotlin `suspend fun` is exposed to Swift as `async throws` by Kotlin/
Native's coroutine bridge, which matches the above signatures.

### Rehydration flow (App.kt)

```kotlin
@Composable
fun WalletSampleApp() {
    val navigator = rememberNavigator()
    val session = remember { WalletSession() }
    var hydrating by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val stored = runCatching { SecureWalletStorageRuntime.get().load() }
            .getOrNull()
        if (stored != null) {
            session.wallet = Wallet.fromMnemonicWithTrustWalletCore(stored)
            navigator.replace(Route.Home)
        }
        hydrating = false
    }

    if (hydrating) {
        // brief splash / empty box — must not flash Welcome
    } else {
        // existing WalletTheme + Box + when(route) body
    }
}
```

A navigator `replace(route)` method is assumed; if only `push` exists,
add a `replace` in the same Navigator file — the replace-root semantic is
exactly what's needed here (no back entry for Welcome after hydration).

### Persist points

- **Create flow** — after the user dismisses the confirm-backup screen
  (same place that currently calls `navigator.push(Route.Home)`).
- **Import flow** — after mnemonic validation succeeds, before
  `navigator.push(Route.Home)`.

Both saves are best-effort: if the storage call throws, log a redacted
warning via the platform's normal log path (no mnemonic in the message)
and continue to Home. The wallet still works in memory for the session.

### Sign-out

Triggered from `AddressHeader`. Dialog copy must state that the wallet
will be removed from this device and that the user needs their backup
phrase to recover. On confirm:

1. `SecureWalletStorageRuntime.get().clear()`
2. `session.wallet = null`
3. `navigator.replace(Route.Welcome)`

No network call, no attempt to drain funds, no final screen — this is a
wallet-removal action, not a transaction.

### Security / hygiene

- `SecureWalletStorage.toString()` on implementations must not reference
  any stored value.
- No `Log.d("…", mnemonic)` anywhere. Not in the happy path, not in
  catch blocks. Exception messages from storage failures that surface to
  the user are generic ("Couldn't save wallet to device").
- The `NoOp` fallback's in-memory slot must not appear in any
  `toString()` or log.
- The Android `EncryptedSharedPreferences` file name and key are
  non-secret constants; committing them is fine.
- Do **not** add a backup/export-phrase UI as part of this spec — users
  still go through the Create flow's existing backup-phrase screen.

---

## Acceptance criteria

1. `./gradlew :sample-compose:compileKotlinJvm` — green.
2. `./gradlew :sample-app:assembleDebug` — green.
3. `./gradlew :sample-compose:linkDebugFrameworkIosSimulatorArm64` — green.
4. `xcodebuild -scheme iosApp -sdk iphonesimulator` — green.
5. End-to-end Android: create a wallet, force-stop the app, relaunch → lands on Home with balances loading (not Welcome).
6. End-to-end iOS: same behavior on simulator.
7. Tapping "Sign out" on Home clears the stored mnemonic; relaunching the app after sign-out lands on Welcome.
8. No `wallet-*` source file is modified — confirm with `git diff --name-only`.
9. `grep -rn "mnemonic" sample-app/src sample-compose/src iosApp/iosApp` yields no new `Log`/`println`/`NSLog` call whose argument contains the mnemonic value.
10. `SecureWalletStorage.toString()` (on every impl, including `NoOp`) returns a constant string with no stored-value reference.
11. The storage file/account names in Android and iOS impls are stable constants (no PRNG, no timestamp suffix) — so a new app version still finds the previously-stored wallet.

---

## Non-goals

- **Biometric gate on every sign.** First-unlock access is the bar; a
  `BiometricPrompt` / `LAContext` gate on each transaction is a separate
  spec.
- **Multiple wallets on one device.** This spec stores exactly one
  mnemonic; a wallet switcher / accounts UI is out of scope.
- **Backup/export from Home.** The Create flow already shows the backup
  phrase once; re-surfacing it from Home is a separate UX decision.
- **Encrypted at rest with a user-chosen PIN.** The platform store is the
  security boundary; no app-level passphrase.
- **iCloud Keychain sync / Android backup service integration.** Both are
  explicitly disabled (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`,
  default non-backup on `EncryptedSharedPreferences`).
- **Migrations.** No existing user data to migrate. A future schema
  change will need its own spec.
- **`wallet-core` API additions.** The storage contract lives in the
  sample modules; promoting it into `wallet-core` is a library-API
  decision to be made deliberately.

---

## Why this is interesting for the experiment

1. **Platform seam fanout.** The spec exercises the same
   `install-an-adapter-from-the-host` pattern already used for Trust
   Wallet Core — good signal on whether agents recognize established
   patterns in the repo rather than inventing parallel mechanisms.
2. **Crypto hygiene under storage.** Persisting the mnemonic is the
   single most security-sensitive thing this showcase does. The spec's
   rules (no logging, redacted `toString`, fixed store key) test whether
   the implementer internalizes CLAUDE.md §4.1 when the temptation to
   "just println for debugging" is highest.
3. **Hot-file edit.** Adding `androidx.security.crypto` touches
   `libs.versions.toml` — serialized per CLAUDE.md §7. This is a clean
   single-agent task; parallelism is not the point.
4. **Lifecycle correctness.** The hydrate-vs-Welcome race is a subtle
   recomposition question — get it wrong and users see a Welcome flash
   before being punted to Home, or worse, the app navigates away from
   Welcome while they're tapping it. A good signal for whether agents
   reason about `LaunchedEffect` timing rather than bolting on
   `delay()`.
