package xyz.wallet.toolkit.sample.flows.swap

import xyz.wallet.toolkit.core.SupportedChain

/**
 * Canonical token list shown before the user types in the picker. Kept
 * short on purpose — this is the "pick something sensible in two taps"
 * entry path, not a directory.
 *
 * Addresses are lowercased so equality checks against 0x/Zerion responses
 * (which may return EIP-55 checksummed forms) work without extra care at
 * call sites.
 */
object DefaultTokens {

    fun forChain(chain: SupportedChain): List<TokenRef> = when (chain) {
        SupportedChain.Ethereum -> ETHEREUM
        SupportedChain.Base -> BASE
        else -> emptyList()
    }

    fun nativeFor(chain: SupportedChain): TokenRef = when (chain) {
        SupportedChain.Ethereum -> ETH_ETHEREUM
        SupportedChain.Base -> ETH_BASE
        else -> error("No native mapped for $chain")
    }

    /** Sensible default buy token when the user lands on the screen. */
    fun defaultBuyFor(chain: SupportedChain): TokenRef = when (chain) {
        SupportedChain.Ethereum -> USDC_ETHEREUM
        SupportedChain.Base -> USDC_BASE
        else -> error("No default buy mapped for $chain")
    }

    private val ETH_ETHEREUM = TokenRef(
        symbol = "ETH", name = "Ether", address = null, decimals = 18,
        chain = SupportedChain.Ethereum, iconUrl = null,
    )
    private val USDC_ETHEREUM = TokenRef(
        symbol = "USDC", name = "USD Coin",
        address = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", decimals = 6,
        chain = SupportedChain.Ethereum, iconUrl = null,
    )
    private val USDT_ETHEREUM = TokenRef(
        symbol = "USDT", name = "Tether USD",
        address = "0xdac17f958d2ee523a2206206994597c13d831ec7", decimals = 6,
        chain = SupportedChain.Ethereum, iconUrl = null,
    )
    private val DAI_ETHEREUM = TokenRef(
        symbol = "DAI", name = "Dai Stablecoin",
        address = "0x6b175474e89094c44da98b954eedeac495271d0f", decimals = 18,
        chain = SupportedChain.Ethereum, iconUrl = null,
    )
    private val WETH_ETHEREUM = TokenRef(
        symbol = "WETH", name = "Wrapped Ether",
        address = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2", decimals = 18,
        chain = SupportedChain.Ethereum, iconUrl = null,
    )
    private val WBTC_ETHEREUM = TokenRef(
        symbol = "WBTC", name = "Wrapped BTC",
        address = "0x2260fac5e5542a773aa44fbcfedf7c193bc2c599", decimals = 8,
        chain = SupportedChain.Ethereum, iconUrl = null,
    )

    private val ETHEREUM: List<TokenRef> = listOf(
        ETH_ETHEREUM, USDC_ETHEREUM, USDT_ETHEREUM, DAI_ETHEREUM,
        WETH_ETHEREUM, WBTC_ETHEREUM,
    )

    private val ETH_BASE = TokenRef(
        symbol = "ETH", name = "Ether", address = null, decimals = 18,
        chain = SupportedChain.Base, iconUrl = null,
    )
    private val USDC_BASE = TokenRef(
        symbol = "USDC", name = "USD Coin",
        address = "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913", decimals = 6,
        chain = SupportedChain.Base, iconUrl = null,
    )
    private val USDBC_BASE = TokenRef(
        symbol = "USDbC", name = "USD Base Coin",
        address = "0xd9aaec86b65d86f6a7b5b1b0c42ffa531710b6ca", decimals = 6,
        chain = SupportedChain.Base, iconUrl = null,
    )
    private val DAI_BASE = TokenRef(
        symbol = "DAI", name = "Dai Stablecoin",
        address = "0x50c5725949a6f0c72e6c4a641f24049a917db0cb", decimals = 18,
        chain = SupportedChain.Base, iconUrl = null,
    )
    private val WETH_BASE = TokenRef(
        symbol = "WETH", name = "Wrapped Ether",
        address = "0x4200000000000000000000000000000000000006", decimals = 18,
        chain = SupportedChain.Base, iconUrl = null,
    )

    private val BASE: List<TokenRef> = listOf(
        ETH_BASE, USDC_BASE, USDBC_BASE, DAI_BASE, WETH_BASE,
    )
}
