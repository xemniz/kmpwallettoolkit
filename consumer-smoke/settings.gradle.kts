pluginManagement {
    val walletToolkitVersion = providers.gradleProperty("walletToolkitVersion")
        .orElse("0.1.1")
        .get()
    plugins {
        id("io.github.xemniz.wallet-toolkit.ios") version walletToolkitVersion
    }
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-wallet-toolkit-consumer-smoke"
