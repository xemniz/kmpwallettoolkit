package xyz.wallet.toolkit.rpc

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RpcClientLifecycleTest {
    @Test
    fun closingBorrowedClientLeavesHttpClientUsableAndRejectsRpcCalls() = runTest {
        var requests = 0
        val http = HttpClient(MockEngine {
            requests++
            respond(
                """{"jsonrpc":"2.0","id":1,"result":"0x1"}""",
                headers = headersOf("Content-Type", "application/json"),
            )
        }) {
            install(ContentNegotiation) { json() }
        }
        try {
            val client = RpcClient("https://rpc.local", http)
            client.close()
            client.close()

            assertTrue(http.coroutineContext.job.isActive)
            assertFailsWith<IllegalStateException> { client.getBalance("0xabc") }
            assertEquals(0, requests)
            assertEquals("0x1", RpcClient("https://rpc.local", http).getBalance("0xabc"))
        } finally {
            http.close()
        }
    }

    @Test
    fun closingOwnedClientClosesHttpClientOnce() = runTest {
        val http = HttpClient(MockEngine { error("No request expected") })
        val client = RpcClient("https://rpc.local", http, ownsHttpClient = true)

        client.close()
        client.close()

        assertTrue(client.isClosed)
        assertFalse(http.coroutineContext.job.isActive)
    }

    @Test
    fun defaultClientRejectsCallsAfterClose() = runTest {
        val client = RpcClient.withDefaults("https://rpc.local")
        client.close()

        assertFailsWith<IllegalStateException> { client.getNonce("0xabc") }
    }
}
