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

    @Test
    fun emptyAccessListIsSerializedAsEmptyArray() {
        val tx = Eip1559Transaction(
            chainId = 1,
            to = "0x3535353535353535353535353535353535353535",
            maxFeePerGasWei = "50000000000",
            maxPriorityFeePerGasWei = "1000000000",
            gasLimit = "21000",
            nonce = 42,
        )

        val json = tx.toSigningPayload().decodeToString()

        assertTrue(json.contains("\"accessList\":[]"), "accessList must be emitted as []: $json")
    }

    @Test
    fun singleEntryAccessListWithEmptyStorageKeysRoundTrips() {
        val original = Eip1559Transaction(
            chainId = 1,
            to = "0x3535353535353535353535353535353535353535",
            maxFeePerGasWei = "50000000000",
            maxPriorityFeePerGasWei = "1000000000",
            gasLimit = "21000",
            nonce = 42,
            accessList = listOf(
                AccessListEntry(
                    address = "0x1111111111111111111111111111111111111111",
                    storageKeys = emptyList(),
                ),
            ),
        )

        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<Eip1559Transaction>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun maxUint256ValueWeiRoundTripsWithoutLoss() {
        val maxUint256 = "115792089237316195423570985008687907853269984665640564039457584007913129639935"
        val original = Eip1559Transaction(
            chainId = 1,
            to = "0x3535353535353535353535353535353535353535",
            valueWei = maxUint256,
            maxFeePerGasWei = "50000000000",
            maxPriorityFeePerGasWei = "1000000000",
            gasLimit = "21000",
            nonce = 42,
        )

        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<Eip1559Transaction>(encoded)

        assertEquals(maxUint256, decoded.valueWei)
        assertEquals(original, decoded)
    }

    @Test
    fun zeroValueDefaultsRoundTripAndAreEmitted() {
        val original = Eip1559Transaction(
            chainId = 1,
            to = "0x3535353535353535353535353535353535353535",
            maxFeePerGasWei = "50000000000",
            maxPriorityFeePerGasWei = "1000000000",
            gasLimit = "21000",
            nonce = 42,
        )

        val json = original.toSigningPayload().decodeToString()
        val decoded = Json.decodeFromString<Eip1559Transaction>(json)

        assertTrue(json.contains("\"valueWei\":\"0\""), "valueWei must be emitted as \"0\": $json")
        assertEquals(original, decoded)
    }
}
