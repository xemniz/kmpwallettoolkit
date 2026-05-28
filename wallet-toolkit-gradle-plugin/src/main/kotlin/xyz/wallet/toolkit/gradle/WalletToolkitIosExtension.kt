package xyz.wallet.toolkit.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class WalletToolkitIosExtension @Inject constructor(
    objects: ObjectFactory,
) {
    val trustWalletCoreGroup: Property<String> = objects.property(String::class.java)
        .convention("io.github.xemniz")

    val trustWalletCoreVersion: Property<String> = objects.property(String::class.java)
        .convention("4.6.0")
}
