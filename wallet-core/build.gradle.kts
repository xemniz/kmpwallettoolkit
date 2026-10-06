plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

val trustWalletCoreIos by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val trustWalletCoreIosSwiftProtobuf by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    trustWalletCoreIos("${libs.trust.wallet.core.ios.get().module}:${libs.versions.trustWalletCore.get()}@zip")
    trustWalletCoreIosSwiftProtobuf("${libs.trust.wallet.core.ios.get().module}:${libs.versions.trustWalletCore.get()}:swiftprotobuf@zip")
}

val extractTrustWalletCoreIos by tasks.registering {
    inputs.files(trustWalletCoreIos)
    outputs.dir(layout.buildDirectory.dir("trustwallet-core-ios/WalletCore.xcframework"))

    doLast {
        copy {
            from(zipTree(trustWalletCoreIos.singleFile))
            into(layout.buildDirectory.dir("trustwallet-core-ios"))
        }
    }
}

val extractTrustWalletCoreIosSwiftProtobuf by tasks.registering {
    inputs.files(trustWalletCoreIosSwiftProtobuf)
    outputs.dir(layout.buildDirectory.dir("trustwallet-core-ios/WalletCoreSwiftProtobuf.xcframework"))

    doLast {
        copy {
            from(zipTree(trustWalletCoreIosSwiftProtobuf.singleFile))
            into(layout.buildDirectory.dir("trustwallet-core-ios"))
        }
    }
}

val extractedTrustWalletCoreIos = layout.buildDirectory.dir("trustwallet-core-ios")

kotlin {
    // wallet-core intentionally exposes Trust Wallet Core through expect/actual platform seams.
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    jvm()
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.compilations.getByName("main") {
            cinterops {
                val walletCore by creating {
                    defFile(project.file("src/nativeInterop/cinterop/WalletCore.def"))
                    tasks.named(interopProcessingTaskName).configure {
                        dependsOn(extractTrustWalletCoreIos, extractTrustWalletCoreIosSwiftProtobuf)
                    }
                    val frameworkRoot = extractedTrustWalletCoreIos.get().asFile.resolve(
                        if (iosTarget.name.contains("Simulator", ignoreCase = true) || iosTarget.name == "iosX64") {
                            "WalletCore.xcframework/ios-arm64_x86_64-simulator"
                        } else {
                            "WalletCore.xcframework/ios-arm64"
                        },
                    )
                    compilerOpts("-F${frameworkRoot.absolutePath}")
                }
            }
        }
        iosTarget.binaries.all {
            val xcframeworkSlice = if (iosTarget.name.contains("Simulator", ignoreCase = true) || iosTarget.name == "iosX64") {
                "ios-arm64_x86_64-simulator"
            } else {
                "ios-arm64"
            }
            val walletCoreRoot = extractedTrustWalletCoreIos.get().asFile.resolve("WalletCore.xcframework/$xcframeworkSlice")
            val swiftProtobufRoot = extractedTrustWalletCoreIos.get().asFile.resolve("WalletCoreSwiftProtobuf.xcframework/$xcframeworkSlice")
            linkerOpts(
                "-F${walletCoreRoot.absolutePath}",
                "-F${swiftProtobufRoot.absolutePath}",
                "-framework",
                "WalletCore",
                "-framework",
                "WalletCoreSwiftProtobuf",
                "-rpath",
                walletCoreRoot.absolutePath,
                "-rpath",
                swiftProtobufRoot.absolutePath,
            )
        }
    }

    android {
        namespace = "xyz.wallet.toolkit.core"
        compileSdk = 36
        minSdk = 24
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            api(project(":wallet-utils"))
        }
        androidMain.dependencies {
            implementation(libs.trust.wallet.core)
        }
        iosX64Main {
            kotlin.srcDir("src/iosWalletCoreMain/kotlin")
        }
        iosArm64Main {
            kotlin.srcDir("src/iosWalletCoreMain/kotlin")
        }
        iosSimulatorArm64Main {
            kotlin.srcDir("src/iosWalletCoreMain/kotlin")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.test.runner)
            }
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
            name.set("KMP Wallet Toolkit Core")
            description.set("Wallet facade, supported chains, and Trust Wallet Core engine boundary for Kotlin Multiplatform apps.")
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
