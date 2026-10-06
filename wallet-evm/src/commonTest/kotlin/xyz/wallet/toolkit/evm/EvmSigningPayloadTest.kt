package xyz.wallet.toolkit.evm

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class EvmSigningPayloadTest {
    @Test
    fun createsStableJsonPayload() {
        val tx = EvmTransaction(
            chainId = 1,
            to = "0xabc",
            valueWei = "100",
            gasPriceWei = "42",
            gasLimit = "21000",
            nonce = 7,
            dataHex = null,
        )

        val payload = tx.toSigningPayload().decodeToString()

        assertTrue(payload.contains("\"to\":\"0xabc\""))
        assertTrue(payload.contains("\"nonce\":7"))
        assertEquals(
            """{"chainId":1,"to":"0xabc","valueWei":"100","gasPriceWei":"42","gasLimit":"21000","nonce":7}""",
            payload,
        )
    }
}

