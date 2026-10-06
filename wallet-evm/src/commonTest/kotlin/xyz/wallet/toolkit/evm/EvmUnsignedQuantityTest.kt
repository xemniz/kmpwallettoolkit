package xyz.wallet.toolkit.evm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EvmUnsignedQuantityTest {
    private val legacy = EvmTransaction(
        chainId = 1,
        to = "0x3535353535353535353535353535353535353535",
        valueWei = "0",
        gasPriceWei = "20000000000",
        gasLimit = "21000",
        nonce = 0,
    )
    private val type2 = Eip1559Transaction(
        chainId = 1,
        to = legacy.to,
        maxFeePerGasWei = "20000000000",
        maxPriorityFeePerGasWei = "1000000000",
        gasLimit = "21000",
        nonce = 0,
    )

    @Test
    fun rejectsNegativeOrSignedLegacyQuantitiesBeforeSerialization() {
        listOf(
            legacy.copy(chainId = -1),
            legacy.copy(nonce = -1),
            legacy.copy(valueWei = "-1"),
            legacy.copy(gasPriceWei = "+1"),
            legacy.copy(gasLimit = " 1"),
            legacy.copy(valueWei = ""),
        ).forEach { tx -> assertFailsWith<IllegalArgumentException> { tx.toSigningPayload() } }
    }

    @Test
    fun rejectsNegativeOrSignedType2QuantitiesBeforeSerialization() {
        listOf(
            type2.copy(chainId = -1),
            type2.copy(nonce = -1),
            type2.copy(valueWei = "-1"),
            type2.copy(maxFeePerGasWei = "+1"),
            type2.copy(maxPriorityFeePerGasWei = "１"),
            type2.copy(gasLimit = ""),
        ).forEach { tx -> assertFailsWith<IllegalArgumentException> { tx.toSigningPayload() } }
    }

    @Test
    fun preservesAcceptedZeroAndLeadingZeroQuantities() {
        val tx = legacy.copy(valueWei = "000", gasPriceWei = "00042")
        assertEquals(
            """{"chainId":1,"to":"0x3535353535353535353535353535353535353535","valueWei":"000","gasPriceWei":"00042","gasLimit":"21000","nonce":0}""",
            tx.toSigningPayload().decodeToString(),
        )
    }
}
