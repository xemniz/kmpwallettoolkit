import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.vanniktech.maven.publish)
    `maven-publish`
    signing
}

version = libs.versions.trustWalletCore.get()

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

val upstreamAar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    upstreamAar(libs.trust.wallet.core.upstream)
}

val upstreamWalletCoreAar = upstreamAar.elements.map { elements ->
    elements.single { it.asFile.extension == "aar" }.asFile
}

val emptySourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "trustwallet-core-android"
            artifact(upstreamWalletCoreAar) {
                extension = "aar"
            }
            artifact(emptySourcesJar)
            artifact(emptyJavadocJar)

            pom {
                name.set("Trust Wallet Core Android")
                description.set("Republished Trust Wallet Core Android AAR for credential-free Maven Central consumption.")
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
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    val protoNode = dependenciesNode.appendNode("dependency")
                    protoNode.appendNode("groupId", project.group.toString())
                    protoNode.appendNode("artifactId", "trustwallet-core-proto")
                    protoNode.appendNode("version", project.version.toString())
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}
