plugins {
    kotlin("multiplatform") version "2.2.20"
    id("com.android.kotlin.multiplatform.library") version "9.1.0"
    id("io.github.xemniz.wallet-toolkit.ios")
}

val walletToolkitVersion = providers.gradleProperty("walletToolkitVersion")
    .orElse("0.1.0-alpha01")

kotlin {
    jvm()
    android {
        namespace = "consumer.smoke"
        compileSdk = 36
        minSdk = 24
    }
    iosX64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.xemniz:wallet-core:${walletToolkitVersion.get()}")
            implementation("io.github.xemniz:wallet-evm:${walletToolkitVersion.get()}")
            implementation("io.github.xemniz:wallet-rpc:${walletToolkitVersion.get()}")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
