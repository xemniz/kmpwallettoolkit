package xyz.wallet.toolkit.rpc

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RpcClientReceiptTest {
    @Test
    fun getTransactionReceiptDecodesAllFields() = runTest {
        val body = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "result": {
                "transactionHash": "0xaaaa",
                "transactionIndex": "0x1",
                "blockHash": "0xbbbb",
                "blockNumber": "0x10",
                "from": "0xfromaddr",
                "to": "0xtoaddr",
                "contractAddress": null,
                "gasUsed": "0x5208",
                "cumulativeGasUsed": "0x5208",
                "status": "0x1",
                "logsBloom": "0x0000",
                "logs": []
              }
            }
        """.trimIndent()
        val client = rpcClient(body)

        val receipt = client.getTransactionReceipt("0xaaaa")

        assertNotNull(receipt)
        assertEquals("0xaaaa", receipt.transactionHash)
        assertEquals("0x1", receipt.transactionIndex)
        assertEquals("0xbbbb", receipt.blockHash)
        assertEquals("0x10", receipt.blockNumber)
        assertEquals("0xfromaddr", receipt.from)
        assertEquals("0xtoaddr", receipt.to)
        assertNull(receipt.contractAddress)
        assertEquals("0x5208", receipt.gasUsed)
        assertEquals("0x5208", receipt.cumulativeGasUsed)
        assertEquals("0x1", receipt.status)
        assertEquals("0x0000", receipt.logsBloom)
        assertTrue(receipt.logs.isEmpty())
    }

    @Test
    fun getTransactionReceiptReturnsNullWhenResultIsJsonNull() = runTest {
        val client = rpcClient("""{"jsonrpc":"2.0","id":1,"result":null}""")

        val receipt = client.getTransactionReceipt("0xnotmined")

        assertNull(receipt)
    }

    @Test
    fun getTransactionReceiptPreservesRevertedStatus() = runTest {
        val body = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "result": {
                "transactionHash": "0xrev",
                "transactionIndex": "0x0",
                "blockHash": "0xblk",
                "blockNumber": "0x11",
                "from": "0xfrom",
                "to": "0xto",
                "contractAddress": null,
                "gasUsed": "0x5208",
                "cumulativeGasUsed": "0x5208",
                "status": "0x0",
                "logsBloom": "0x0000",
                "logs": []
              }
            }
        """.trimIndent()
        val client = rpcClient(body)

        val receipt = client.getTransactionReceipt("0xrev")

        assertNotNull(receipt)
        assertEquals("0x0", receipt.status)
    }

    @Test
    fun getTransactionReceiptContractCreation() = runTest {
        val body = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "result": {
                "transactionHash": "0xccc",
                "transactionIndex": "0x0",
                "blockHash": "0xblk",
                "blockNumber": "0x12",
                "from": "0xdeployer",
                "to": null,
                "contractAddress": "0xdeadbeef",
                "gasUsed": "0x100000",
                "cumulativeGasUsed": "0x100000",
                "status": "0x1",
                "logsBloom": "0x0000",
                "logs": []
              }
            }
        """.trimIndent()
        val client = rpcClient(body)

        val receipt = client.getTransactionReceipt("0xccc")

        assertNotNull(receipt)
        assertNull(receipt.to)
        assertEquals("0xdeadbeef", receipt.contractAddress)
    }

    @Test
    fun getTransactionReceiptIgnoresUnknownFields() = runTest {
        // Drift guard: simulate an EIP-4844 node sending blobGasUsed alongside
        // the classic fields. Decoding must succeed and ignore the unknown key.
        val body = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "result": {
                "transactionHash": "0xblob",
                "transactionIndex": "0x2",
                "blockHash": "0xblk",
                "blockNumber": "0x13",
                "from": "0xfrom",
                "to": "0xto",
                "contractAddress": null,
                "gasUsed": "0x5208",
                "cumulativeGasUsed": "0x5208",
                "status": "0x1",
                "logsBloom": "0x0000",
                "logs": [],
                "blobGasUsed": "0x20000",
                "effectiveGasPrice": "0x77359400",
                "type": "0x3"
              }
            }
        """.trimIndent()
        val client = rpcClient(body)

        val receipt = client.getTransactionReceipt("0xblob")

        assertNotNull(receipt)
        assertEquals("0xblob", receipt.transactionHash)
        assertEquals("0x1", receipt.status)
    }
}

private fun rpcClient(jsonResponse: String): RpcClient {
    val engine = MockEngine { _: HttpRequestData ->
        respond(
            content = jsonResponse,
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", "application/json"),
        )
    }

    val httpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    return RpcClient(
        baseUrl = "https://rpc.local",
        httpClient = httpClient,
        json = Json,
    )
}
