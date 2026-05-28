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

val upstreamProto by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    upstreamProto(libs.trust.wallet.core.proto.upstream)
}

val upstreamProtoJar = upstreamProto.elements.map { elements ->
    elements.single {
        it.asFile.name == "wallet-core-proto-${libs.versions.trustWalletCore.get()}.jar"
    }.asFile
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
            artifactId = "trustwallet-core-proto"
            artifact(upstreamProtoJar)
            artifact(emptySourcesJar)
            artifact(emptyJavadocJar)

            pom {
                name.set("Trust Wallet Core Proto")
                description.set("Republished Trust Wallet Core protobuf Java Lite bindings for credential-free Maven Central consumption.")
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
                    val protobufNode = dependenciesNode.appendNode("dependency")
                    protobufNode.appendNode("groupId", "com.google.protobuf")
                    protobufNode.appendNode("artifactId", "protobuf-javalite")
                    protobufNode.appendNode("version", libs.versions.protobufJavalite.get())
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}
