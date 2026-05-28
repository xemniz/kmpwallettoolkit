plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.maven.publish)
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("walletToolkitIos") {
            id = "io.github.xemniz.wallet-toolkit.ios"
            implementationClass = "xyz.wallet.toolkit.gradle.WalletToolkitIosPlugin"
            displayName = "KMP Wallet Toolkit iOS Linker"
            description = "Links KMP Wallet Toolkit iOS consumers against republished Trust Wallet Core XCFrameworks."
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
            name.set("KMP Wallet Toolkit Gradle Plugin")
            description.set("Gradle plugin for wiring KMP Wallet Toolkit iOS consumers to Trust Wallet Core XCFrameworks.")
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
