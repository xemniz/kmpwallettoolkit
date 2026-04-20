package xyz.wallet.toolkit.evm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Eip1559SigningPayloadTest {
    @Test
    fun roundTripsAllFields() {
        val original = Eip1559Transaction(
            chainId = 1,
            to = "0x3535353535353535353535353535353535353535",
            valueWei = "1000000000000000000",
            maxFeePerGasWei = "50000000000",
            maxPriorityFeePerGasWei = "1000000000",
            gasLimit = "21000",
            nonce = 42,
            dataHex = "0xdeadbeef",
            accessList = listOf(
                AccessListEntry(
                    address = "0x1111111111111111111111111111111111111111",
                    storageKeys = listOf(
                        "0x0000000000000000000000000000000000000000000000000000000000000001",
                        "0x0000000000000000000000000000000000000000000000000000000000000002",
                    ),
                ),
                AccessListEntry(
                    address = "0x2222222222222222222222222222222222222222",
                    storageKeys = listOf(
                        "0x0000000000000000000000000000000000000000000000000000000000000003",
                    ),
                ),
            ),
        )

        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<Eip1559Transaction>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun fieldOrderingFollowsDeclarationOrder() {
        val tx = Eip1559Transaction(
            chainId = 1,
            to = "0x3535353535353535353535353535353535353535",
            maxFeePerGasWei = "50000000000",
            maxPriorityFeePerGasWei = "1000000000",
            gasLimit = "21000",
            nonce = 42,
        )

        val json = tx.toSigningPayload().decodeToString()

        assertTrue(json.startsWith("{\"chainId\":"), "unexpected start: $json")
        val gasLimitIdx = json.indexOf("\"gasLimit\":")
        val nonceIdx = json.indexOf("\"nonce\":")
        assertTrue(gasLimitIdx >= 0 && nonceIdx >= 0, "missing keys: $json")
        assertTrue(nonceIdx > gasLimitIdx, "nonce must come after gasLimit: $json")
    }
}
