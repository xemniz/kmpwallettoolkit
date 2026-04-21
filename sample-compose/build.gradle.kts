plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidLibrary {
        namespace = "xyz.wallet.toolkit.sample"
        compileSdk = 36
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":wallet-core"))
            api(project(":wallet-rpc"))
            api(project(":wallet-evm"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}
