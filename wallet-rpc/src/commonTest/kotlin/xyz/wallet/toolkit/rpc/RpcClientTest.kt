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

class RpcClientTest {
    @Test
    fun getBalanceReturnsHexBalance() = runTest {
        val client = rpcClientWithResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x1\"}")

        val balance = client.getBalance("0xabc")

        assertEquals("0x1", balance)
    }

    @Test
    fun sendRawTransactionReturnsHash() = runTest {
        val client = rpcClientWithResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0xdeadbeef\"}")

        val txHash = client.sendRawTransaction("0xsigned")

        assertEquals("0xdeadbeef", txHash)
    }
}

private fun rpcClientWithResponse(jsonResponse: String): RpcClient {
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


