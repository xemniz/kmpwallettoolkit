package xyz.wallet.toolkit.evm

import kotlin.test.Test
import kotlin.test.assertEquals

private const val EXPECTED_JSON =
    "{\"chainId\":1," +
        "\"to\":\"0x3535353535353535353535353535353535353535\"," +
        "\"valueWei\":\"1000000000000000000\"," +
        "\"maxFeePerGasWei\":\"50000000000\"," +
        "\"maxPriorityFeePerGasWei\":\"1000000000\"," +
        "\"gasLimit\":\"21000\"," +
        "\"nonce\":42," +
        "\"dataHex\":null," +
        "\"accessList\":[]}"

class Eip1559GoldenVectorTest {
    @Test
    fun goldenJsonDoesNotDrift() {
        val tx = Eip1559Transaction(
            chainId = 1,
            to = "0x3535353535353535353535353535353535353535",
            valueWei = "1000000000000000000",
            maxFeePerGasWei = "50000000000",
            maxPriorityFeePerGasWei = "1000000000",
            gasLimit = "21000",
            nonce = 42,
            dataHex = null,
            accessList = emptyList(),
        )

        val actual = tx.toSigningPayload().decodeToString()

        assertEquals(EXPECTED_JSON, actual)
    }
}
