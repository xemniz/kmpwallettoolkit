# Release Checklist

Use this checklist for every Maven Central release.

## Preflight

- Confirm the release version, for example `0.1.1`.
- Confirm the Trust Wallet Core wrapper version in `gradle/libs.versions.toml`.
- Review `docs/TRUST_WALLET_CORE_DISTRIBUTION.md` and verify the upstream XCFramework SHA-256 values still match the release assets.
- Confirm `settings.gradle.kts` does not require `mavenLocal()` or Trust Wallet GitHub Packages credentials for normal consumer builds.
- Confirm no credential values appear in source, docs, Gradle output, or shell history snippets copied into the release notes.

## Verification

```bash
./gradlew :wallet-utils:allTests \
  :wallet-core:allTests \
  :wallet-evm:allTests \
  :wallet-rpc:allTests \
  :wallet-toolkit-gradle-plugin:compileKotlin \
  :wallet-core:compileKotlinIosX64 \
  :wallet-core:compileKotlinJvm
```

```bash
./gradlew -PwalletToolkitVersion=0.1.1 \
  :trustwallet-core-proto:publishToMavenLocal \
  :trustwallet-core-android:publishToMavenLocal \
  :trustwallet-core-ios:publishToMavenLocal \
  :wallet-toolkit-gradle-plugin:publishToMavenLocal \
  :wallet-utils:publishToMavenLocal \
  :wallet-core:publishToMavenLocal \
  :wallet-evm:publishToMavenLocal \
  :wallet-rpc:publishToMavenLocal
```

```bash
./gradlew -p consumer-smoke -PwalletToolkitVersion=0.1.1 \
  :compileAndroidMain \
  :compileKotlinIosSimulatorArm64 \
  :iosSimulatorArm64Test
```

## Publish

Publish wrapper artifacts first, then toolkit artifacts:

```bash
./gradlew -PwalletToolkitVersion=0.1.1 \
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

Release the deployment from Central Portal after validation, or use `publishAndReleaseToMavenCentral` when the release should be automatic.

## Post-Publish

- Run the sample in Maven client mode:

```bash
./gradlew -PwalletToolkitDependencyMode=maven \
  :sample-compose:compileAndroidMain \
  :sample-compose:linkDebugFrameworkIosSimulatorArm64 \
  :sample-app:assembleDebug
```

- Install the Android sample in Maven client mode:

```bash
./gradlew -PwalletToolkitDependencyMode=maven :sample-app:installDebug
```

- Optionally run `consumer-smoke` against Maven Central after the artifacts are available.
- Tag the release in Git after Maven Central availability is confirmed.
