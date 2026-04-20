import WalletCore
import SampleCompose

/// Real Trust Wallet Core adapter that bridges the Kotlin `TrustWalletCoreIosAdapter`
/// protocol to the native WalletCore iOS SDK.
class RealTrustWalletCoreAdapter: TrustWalletCoreIosAdapter {

    func createMnemonic() -> String {
        guard let wallet = HDWallet(strength: 128, passphrase: "") else {
            fatalError("HDWallet: failed to create wallet")
        }
        return wallet.mnemonic
    }

    func deriveAddress(mnemonic: String, chain: SupportedChain) -> String {
        guard let wallet = HDWallet(mnemonic: mnemonic, passphrase: "") else {
            fatalError("HDWallet: invalid mnemonic")
        }
        return wallet.getAddressForCoin(coin: toCoinType(chain))
    }

    func signEvmTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: TrustWalletCoreEvmSigningRequest
    ) -> String {
        guard let wallet = HDWallet(mnemonic: mnemonic, passphrase: "") else {
            return ""
        }
        let privateKey = wallet.getKeyForCoin(coin: toCoinType(chain))

        var transfer = EthereumTransaction.Transfer()
        transfer.amount = decimalToData(transaction.valueWei)
        if let hex = transaction.dataHex, !hex.isEmpty {
            transfer.data = Data(hexString: hex) ?? Data()
        }

        var ethTx = EthereumTransaction()
        ethTx.transfer = transfer

        var input = EthereumSigningInput()
        input.chainID = int64ToData(transaction.chainId)
        input.nonce = int64ToData(transaction.nonce)
        input.gasPrice = decimalToData(transaction.gasPriceWei)
        input.gasLimit = decimalToData(transaction.gasLimit)
        input.toAddress = transaction.to
        input.privateKey = privateKey.data
        input.transaction = ethTx

        guard let output: EthereumSigningOutput =
                try? AnySigner.sign(input: input, coin: .ethereum) else {
            return ""
        }
        return "0x" + output.encoded.hexString
    }

    // MARK: - Helpers

    private func toCoinType(_ chain: SupportedChain) -> CoinType {
        if chain == SupportedChain.polygon { return .polygon }
        return .ethereum // Ethereum, Base, Arbitrum all use the Ethereum coin type
    }

    private func int64ToData(_ value: Int64) -> Data {
        return decimalToData(String(value))
    }

    /// Convert a decimal number string (e.g. "1000000000") to big-endian unsigned bytes,
    /// mirroring Android's `decimalToByteString()`.
    private func decimalToData(_ decimal: String) -> Data {
        guard !decimal.isEmpty, decimal != "0" else { return Data([0]) }

        var digits = Array(decimal).compactMap { $0.wholeNumberValue }
        guard !digits.isEmpty else { return Data([0]) }

        var bytes: [UInt8] = []
        while !digits.allSatisfy({ $0 == 0 }) {
            var remainder = 0
            for i in 0..<digits.count {
                let current = remainder * 10 + digits[i]
                digits[i] = current / 256
                remainder = current % 256
            }
            bytes.append(UInt8(remainder))
            while digits.count > 1 && digits[0] == 0 {
                digits.removeFirst()
            }
        }

        return Data(bytes.isEmpty ? [0] : bytes.reversed())
    }
}

