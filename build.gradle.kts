// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.wallet.toolkit.ios) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

allprojects {
    group = "io.github.xemniz"
    version = providers.gradleProperty("walletToolkitVersion")
        .orElse("0.1.0-SNAPSHOT")
        .get()

    if (providers.gradleProperty("useUpstreamTrustWalletCore").orNull == "true") {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution {
                substitute(module("io.github.xemniz:trustwallet-core-android"))
                    .using(module("com.trustwallet:wallet-core:${libs.versions.trustWalletCore.get()}"))
                    .because("Development-only shortcut. Do not use for release publishing because resolved metadata would leak the authenticated upstream coordinate.")
                substitute(module("io.github.xemniz:trustwallet-core-proto"))
                    .using(module("com.trustwallet:wallet-core-proto:${libs.versions.trustWalletCore.get()}"))
                    .because("Development-only shortcut. Do not use for release publishing because resolved metadata would leak the authenticated upstream coordinate.")
            }
        }
    }
}
