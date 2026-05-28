pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("useMavenLocal").orNull == "true") {
            mavenLocal()
        }
        google()
        mavenCentral()

        val trustWalletGithubUser = providers.gradleProperty("gpr.user")
            .orElse(providers.environmentVariable("GPR_USER"))
        val trustWalletGithubKey = providers.gradleProperty("gpr.key")
            .orElse(providers.environmentVariable("GPR_KEY"))
        if (trustWalletGithubUser.isPresent && trustWalletGithubKey.isPresent) {
            maven {
                url = uri("https://maven.pkg.github.com/trustwallet/wallet-core")
                mavenContent {
                    includeGroup("com.trustwallet")
                }
                credentials {
                    username = trustWalletGithubUser.get()
                    password = trustWalletGithubKey.get()
                }
            }
        }
    }
}

rootProject.name = "kmp-wallet-toolkit"
include(":app")
include(":trustwallet-core-android")
include(":trustwallet-core-ios")
include(":trustwallet-core-proto")
include(":wallet-toolkit-gradle-plugin")
include(":wallet-core")
include(":wallet-evm")
include(":wallet-rpc")
include(":wallet-utils")
include(":sample-compose")
include(":sample-app")
