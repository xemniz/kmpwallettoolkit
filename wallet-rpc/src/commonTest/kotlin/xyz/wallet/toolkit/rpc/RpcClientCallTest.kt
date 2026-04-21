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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RpcClientCallTest {
    @Test
    fun ethCallReturnsExactHexResult() = runTest {
        val client = rpcClient(
            """{"jsonrpc":"2.0","id":1,"result":"0x000000000000000000000000000000000000000000000000000000000000002a"}"""
        )

        val result = client.ethCall(
            RpcCall(to = "0xcontract", data = "0x70a08231"),
        )

        assertEquals(
            "0x000000000000000000000000000000000000000000000000000000000000002a",
            result,
        )
    }

    @Test
    fun ethCallSurfacesRevertAsRpcException() = runTest {
        val client = rpcClient(
            """{"jsonrpc":"2.0","id":1,"error":{"code":3,"message":"execution reverted"}}"""
        )

        val ex = assertFailsWith<RpcException> {
            client.ethCall(RpcCall(to = "0xcontract", data = "0xdeadbeef"))
        }

        assertEquals(3, ex.code)
        assertTrue(
            ex.message.contains("execution reverted"),
            "expected message to contain 'execution reverted', was: ${ex.message}",
        )
    }

    @Test
    fun getCodeReturnsZeroXForEoa() = runTest {
        val client = rpcClient("""{"jsonrpc":"2.0","id":1,"result":"0x"}""")

        val code = client.getCode("0xeoa")

        assertEquals("0x", code)
    }

    @Test
    fun getCodeReturnsBytecodeForContract() = runTest {
        val bytecode = "0x6080604052348015600f57600080fd5b50"
        val client = rpcClient("""{"jsonrpc":"2.0","id":1,"result":"$bytecode"}""")

        val code = client.getCode("0xcontract")

        assertEquals(bytecode, code)
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
