package xyz.wallet.toolkit.sample.di

import xyz.wallet.toolkit.core.TrustWalletCoreNativeBridge
import xyz.wallet.toolkit.sample.execution.ExecutionJournalStore
import xyz.wallet.toolkit.sample.execution.RpcExecutionClient
import xyz.wallet.toolkit.sample.execution.TransactionExecutionRepository
import xyz.wallet.toolkit.sample.flows.swap.SwapQuoteSource
import xyz.wallet.toolkit.sample.rpc.RpcClientFactory
import xyz.wallet.toolkit.sample.state.SecureWalletStorageRuntime
import xyz.wallet.toolkit.sample.state.WalletSession
import xyz.wallet.toolkit.utils.toHexString

internal fun executionRepository(session: WalletSession, quotes: SwapQuoteSource): TransactionExecutionRepository {
    val storage = SecureWalletStorageRuntime.getOperationStorage()
    val repository = TransactionExecutionRepository(
        journal = object : ExecutionJournalStore {
            override fun load(): String? {
                val saved = storage.loadJournal()
                check(!storage.journalReadFailed) { "Transaction storage unavailable" }
                return saved
            }
            override fun save(serialized: String): Boolean = storage.saveJournal(serialized)
        },
        rpcForChain = { RpcExecutionClient(RpcClientFactory.forChain(it)) },
        hashSignedTransaction = { TrustWalletCoreNativeBridge.evmTransactionHash(it).toHexString() },
        refreshQuote = { chain, owner, quote ->
            quotes.fetchQuote(chain, quote.sell, quote.buy, quote.sellAmountRaw, owner)
        },
    )
    session.addInvalidationListener {
        repository.revokeSession()
        RpcClientFactory.closeAll()
    }
    return repository
}
