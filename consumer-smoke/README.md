# Consumer Smoke

Standalone Kotlin Multiplatform project that consumes `kmp-wallet-toolkit` through `io.github.xemniz` Maven coordinates instead of project dependencies.

Run from the repository root after the release is available on Maven Central:

```bash
./gradlew -p consumer-smoke -PwalletToolkitVersion=0.1.1 \
  :compileAndroidMain \
  :compileKotlinIosSimulatorArm64 \
  :iosSimulatorArm64Test
```

The smoke project uses Maven coordinates instead of project dependencies. It includes `mavenLocal()` so maintainers can test a locally published replacement version; released-client validation should use the main sample with `-PwalletToolkitDependencyMode=maven`, where this repository keeps `mavenLocal()` disabled by default.

To test a different locally published or pre-release version:

```bash
./gradlew -p consumer-smoke -PwalletToolkitVersion=<version> allTests
```

The release smoke gate compiles Android and iOS explicitly:

```bash
./gradlew -p consumer-smoke -PwalletToolkitVersion=0.1.1 \
  :compileAndroidMain \
  :compileKotlinIosSimulatorArm64 \
  :iosSimulatorArm64Test
```

This project is intentionally not included from the root `settings.gradle.kts`.
