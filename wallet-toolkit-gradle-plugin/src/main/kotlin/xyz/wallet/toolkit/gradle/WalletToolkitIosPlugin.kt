package xyz.wallet.toolkit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

class WalletToolkitIosPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "walletToolkitIos",
            WalletToolkitIosExtension::class.java,
        )

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val walletCore = project.createTrustWalletCoreConfiguration(
                name = "walletToolkitTrustWalletCoreIos",
                classifier = null,
                extension = extension,
            )
            val swiftProtobuf = project.createTrustWalletCoreConfiguration(
                name = "walletToolkitTrustWalletCoreIosSwiftProtobuf",
                classifier = "swiftprotobuf",
                extension = extension,
            )
            val extractWalletCore = project.registerExtractTask(
                name = "extractWalletToolkitTrustWalletCoreIos",
                configuration = walletCore,
                outputChild = "WalletCore.xcframework",
            )
            val extractSwiftProtobuf = project.registerExtractTask(
                name = "extractWalletToolkitTrustWalletCoreIosSwiftProtobuf",
                configuration = swiftProtobuf,
                outputChild = "WalletCoreSwiftProtobuf.xcframework",
            )
            val extractedRoot = project.layout.buildDirectory.dir("wallet-toolkit/trustwallet-core-ios")

            project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
                kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
                    if (!target.isIosTarget()) return@configureEach

                    target.binaries.configureEach { binary ->
                        binary.linkTaskProvider.configure { linkTask ->
                            linkTask.dependsOn(extractWalletCore, extractSwiftProtobuf)
                        }

                        val slice = if (target.isSimulatorTarget()) {
                            "ios-arm64_x86_64-simulator"
                        } else {
                            "ios-arm64"
                        }
                        val walletCoreRoot = extractedRoot.get().asFile.resolve("WalletCore.xcframework/$slice")
                        val swiftProtobufRoot = extractedRoot.get().asFile.resolve("WalletCoreSwiftProtobuf.xcframework/$slice")
                        binary.linkerOpts(
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
            }
        }
    }

    private fun Project.createTrustWalletCoreConfiguration(
        name: String,
        classifier: String?,
        extension: WalletToolkitIosExtension,
    ): Configuration {
        val dependencyHandler = dependencies
        return configurations.create(name).also { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.defaultDependencies { dependencies ->
                val notation = buildString {
                    append(extension.trustWalletCoreGroup.get())
                    append(":trustwallet-core-ios:")
                    append(extension.trustWalletCoreVersion.get())
                    if (classifier != null) {
                        append(":")
                        append(classifier)
                    }
                    append("@zip")
                }
                dependencies.add(dependencyHandler.create(notation))
            }
        }
    }

    private fun Project.registerExtractTask(
        name: String,
        configuration: Configuration,
        outputChild: String,
    ) = tasks.register(name) {
        val outputRoot = layout.buildDirectory.dir("wallet-toolkit/trustwallet-core-ios")
        it.inputs.files(configuration)
        it.outputs.dir(outputRoot.map { root -> root.dir(outputChild) })

        it.doLast {
            copy { spec ->
                spec.from(zipTree(configuration.singleFile))
                spec.into(outputRoot)
            }
        }
    }

    private fun KotlinNativeTarget.isIosTarget(): Boolean =
        name == "iosArm64" || name == "iosX64" || name == "iosSimulatorArm64"

    private fun KotlinNativeTarget.isSimulatorTarget(): Boolean =
        name == "iosX64" || name == "iosSimulatorArm64"
}
