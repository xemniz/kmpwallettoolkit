# Publishing

The toolkit modules are configured for standard Kotlin Multiplatform Maven publication:

- `io.github.xemniz:trustwallet-core-android`
- `io.github.xemniz:trustwallet-core-ios`
- `io.github.xemniz:trustwallet-core-proto`
- `io.github.xemniz:wallet-toolkit-gradle-plugin`
- `io.github.xemniz:wallet-utils`
- `io.github.xemniz:wallet-core`
- `io.github.xemniz:wallet-evm`
- `io.github.xemniz:wallet-rpc`

Source checkouts default to a snapshot version for local development. Override it without editing source when testing or publishing a release:

```bash
./gradlew -PwalletToolkitVersion=0.1.0-alpha01 :wallet-core:publishToMavenLocal
```

The republished Trust Wallet Core artifacts use the upstream Trust Wallet Core version, currently `4.6.0`, not `walletToolkitVersion`.

Consumers should not publish anything locally and do not need Trust Wallet credentials. They should use `google()` and `mavenCentral()` with the released coordinates from `README.md`.

For maintainer testing before a release, publish all library modules locally:

```bash
./gradlew -PwalletToolkitVersion=0.1.0-alpha01 \
  :trustwallet-core-proto:publishToMavenLocal \
  :trustwallet-core-android:publishToMavenLocal \
  :trustwallet-core-ios:publishToMavenLocal \
  :wallet-toolkit-gradle-plugin:publishToMavenLocal \
  :wallet-utils:publishToMavenLocal \
  :wallet-core:publishToMavenLocal \
  :wallet-evm:publishToMavenLocal \
  :wallet-rpc:publishToMavenLocal
```

Then add `mavenLocal()` to a throwaway consuming KMM app, or run this repo with `-PuseMavenLocal=true` when you explicitly need local artifacts to override Maven Central.

For the included standalone smoke consumer:

```bash
./gradlew -p consumer-smoke -PwalletToolkitVersion=0.1.0-alpha01 \
  :compileAndroidMain \
  :compileKotlinIosSimulatorArm64 \
  :iosSimulatorArm64Test
```

## Maven Central

Maven Central publishing uses the Central Portal through the Vanniktech Maven Publish plugin.

Before publishing:

- Verify the `io.github.xemniz` namespace in the Central Portal.
- Create a Central Portal user token.
- Configure a GPG signing key and publish the public key to a public keyserver.

Local machine:

```properties
# ~/.gradle/gradle.properties
mavenCentralUsername=your-central-token-username
mavenCentralPassword=your-central-token-password

# Option A: in-memory signing key
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----...
signingInMemoryKeyId=12345678
signingInMemoryKeyPassword=your-gpg-passphrase

# Option B: local GPG keyring
# signing.keyId=12345678
# signing.password=your-gpg-passphrase
# signing.secretKeyRingFile=/Users/you/.gnupg/secring.gpg
```

CI environment variables:

```text
ORG_GRADLE_PROJECT_mavenCentralUsername
ORG_GRADLE_PROJECT_mavenCentralPassword
ORG_GRADLE_PROJECT_signingInMemoryKey
ORG_GRADLE_PROJECT_signingInMemoryKeyId
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
GPR_USER
GPR_KEY
```

The included GitHub Actions release workflow expects these repository secrets:

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_IN_MEMORY_KEY
SIGNING_IN_MEMORY_KEY_ID
SIGNING_IN_MEMORY_KEY_PASSWORD
TRUST_WALLET_GPR_USER
TRUST_WALLET_GPR_KEY
```

These are maintainer/publishing credentials only. App developers consuming the toolkit do not need them.

Upload a release deployment to Maven Central:

```bash
./gradlew -PwalletToolkitVersion=0.1.0-alpha01 \
  :trustwallet-core-proto:publishToMavenCentral \
  :trustwallet-core-android:publishToMavenCentral \
  :trustwallet-core-ios:publishToMavenCentral \
  :wallet-toolkit-gradle-plugin:publishToMavenCentral \
  :wallet-utils:publishToMavenCentral \
  :wallet-core:publishToMavenCentral \
  :wallet-evm:publishToMavenCentral \
  :wallet-rpc:publishToMavenCentral \
  --no-configuration-cache
```

After upload, open the Central Portal deployments page, wait for validation, and publish the deployment. To publish automatically after validation, use `publishAndReleaseToMavenCentral`.

Publishing Trust Wallet Core wrapper artifacts from source may need Trust Wallet GitHub Packages credentials on the publishing machine because the Android and proto wrapper modules fetch upstream `com.trustwallet` artifacts before republishing them under `io.github.xemniz`. Set them as Gradle properties or environment variables:

```properties
# ~/.gradle/gradle.properties
gpr.user=your-github-username
gpr.key=your-github-token-with-package-read
```

```bash
export GPR_USER=your-github-username
export GPR_KEY=your-github-token-with-package-read
```

Consumers never need these credentials. They resolve only `io.github.xemniz:*` artifacts from Maven Central.

Do not commit credentials. If a token is pasted into chat, logs, or source control, revoke it and create a replacement before publishing.

## Trust Wallet Core republishing

`wallet-core` depends on `io.github.xemniz:trustwallet-core-android` for its Android target. That artifact is a byte-for-byte republish of Trust Wallet Core's upstream Android AAR under Central-resolvable coordinates, and it depends on the republished `io.github.xemniz:trustwallet-core-proto` jar.

`io.github.xemniz:trustwallet-core-ios` republishes Trust Wallet Core's upstream `WalletCore.xcframework.zip` plus the `WalletCoreSwiftProtobuf.xcframework.zip` classifier with SHA-256 verification before publication.

iOS consumers should apply the `io.github.xemniz.wallet-toolkit.ios` Gradle plugin at the app/shared module that builds iOS targets. The plugin resolves `io.github.xemniz:trustwallet-core-ios` from Maven Central and adds the required linker options for device and simulator builds.

This source checkout can still compile directly against upstream GitHub Packages while developing wrapper changes:

```bash
./gradlew -PuseUpstreamTrustWalletCore=true :wallet-core:compileKotlinAndroid
```

Do not use `-PuseUpstreamTrustWalletCore=true` for release publishing. It is a development-only shortcut; release metadata must keep the `io.github.xemniz:trustwallet-core-*` coordinates so consumers do not need GitHub Packages credentials.
