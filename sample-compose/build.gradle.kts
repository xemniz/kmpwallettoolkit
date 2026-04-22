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

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SampleCompose"
            isStatic = true
            export(project(":wallet-core"))
            export(project(":wallet-evm"))
            export(project(":wallet-rpc"))
        }
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
