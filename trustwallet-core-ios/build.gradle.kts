import java.net.URI
import java.security.MessageDigest
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.vanniktech.maven.publish)
    `maven-publish`
    signing
}

version = libs.versions.trustWalletCore.get()

private val walletCoreUrl = "https://github.com/trustwallet/wallet-core/releases/download/${version}/WalletCore.xcframework.zip"
private val walletCoreSha256 = "689935aff413004b18c7b32ee955716868ebcd38328c5159c69f0d5f5bcfddf0"
private val swiftProtobufUrl = "https://github.com/trustwallet/wallet-core/releases/download/${version}/WalletCoreSwiftProtobuf.xcframework.zip"
private val swiftProtobufSha256 = "719b1ebc7ad174017e399cdd7fc60372b369d9712d646ebb8b4e264c4881d1d8"

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

fun Project.registerDownloadTask(
    name: String,
    url: String,
    sha256: String,
    outputFileName: String,
) = tasks.register(name) {
    val outputFile = layout.buildDirectory.file("downloads/$outputFileName")
    outputs.file(outputFile)

    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        if (!file.exists()) {
            URI(url).toURL().openStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString(separator = "") { "%02x".format(it) }
        check(digest == sha256) {
            "Unexpected SHA-256 for $outputFileName: $digest"
        }
    }
}

val downloadWalletCore by registerDownloadTask(
    name = "downloadWalletCoreXcframework",
    url = walletCoreUrl,
    sha256 = walletCoreSha256,
    outputFileName = "WalletCore.xcframework.zip",
)

val downloadSwiftProtobuf by registerDownloadTask(
    name = "downloadWalletCoreSwiftProtobufXcframework",
    url = swiftProtobufUrl,
    sha256 = swiftProtobufSha256,
    outputFileName = "WalletCoreSwiftProtobuf.xcframework.zip",
)

val walletCoreZip = layout.buildDirectory.file("downloads/WalletCore.xcframework.zip")
val swiftProtobufZip = layout.buildDirectory.file("downloads/WalletCoreSwiftProtobuf.xcframework.zip")

val emptySourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "trustwallet-core-ios"
            artifact(walletCoreZip) {
                builtBy(downloadWalletCore)
                extension = "zip"
            }
            artifact(swiftProtobufZip) {
                builtBy(downloadSwiftProtobuf)
                classifier = "swiftprotobuf"
                extension = "zip"
            }
            artifact(emptySourcesJar)
            artifact(emptyJavadocJar)

            pom {
                name.set("Trust Wallet Core iOS")
                description.set("Republished Trust Wallet Core iOS XCFrameworks for credential-free Maven Central consumption.")
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
}

signing {
    sign(publishing.publications["maven"])
}
