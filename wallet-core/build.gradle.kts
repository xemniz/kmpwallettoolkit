plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    // wallet-core intentionally exposes Trust Wallet Core through expect/actual platform seams.
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "xyz.wallet.toolkit.core"
        compileSdk = 36
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":wallet-utils"))
        }
        androidMain.dependencies {
            implementation(libs.trust.wallet.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}


