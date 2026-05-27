plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // sample-compose uses expect/actual adapters for host platform services.
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.navigation3.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
