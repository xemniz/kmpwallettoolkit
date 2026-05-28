plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "xyz.wallet.toolkit.utils"
        compileSdk = 36
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

publishing {
    publications.withType<org.gradle.api.publish.maven.MavenPublication>().configureEach {
        pom {
            name.set("KMP Wallet Toolkit Utils")
            description.set("Hex, encoding, and BIP-39 utility helpers for Kotlin Multiplatform wallet apps.")
            url.set("https://github.com/xemniz/kmp-wallet-toolkit")
            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("xemniz")
                    name.set("xemniz")
                }
            }
            scm {
                url.set("https://github.com/xemniz/kmp-wallet-toolkit")
            }
        }
    }
}
