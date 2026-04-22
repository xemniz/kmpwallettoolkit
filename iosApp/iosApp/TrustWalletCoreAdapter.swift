import Foundation
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

    func signEip1559(
        mnemonic: String,
        chain: SupportedChain,
        signingPayloadJson: KotlinByteArray
    ) -> String {
        let bytes: [UInt8] = (0..<signingPayloadJson.size).map {
            UInt8(bitPattern: signingPayloadJson.get(index: $0))
        }
        let data = Data(bytes)
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            fatalError("EIP-1559 signing payload is not valid JSON")
        }

        guard
            let chainId = (json["chainId"] as? NSNumber)?.int64Value
                ?? Int64((json["chainId"] as? String) ?? ""),
            let to = json["to"] as? String,
            let maxFeePerGasWei = json["maxFeePerGasWei"] as? String,
            let maxPriorityFeePerGasWei = json["maxPriorityFeePerGasWei"] as? String,
            let gasLimit = json["gasLimit"] as? String,
            let nonce = (json["nonce"] as? NSNumber)?.int64Value
                ?? Int64((json["nonce"] as? String) ?? "")
        else {
            fatalError("EIP-1559 signing payload missing required field")
        }
        let valueWei = (json["valueWei"] as? String) ?? "0"
        let dataHex: String? = {
            guard let raw = json["dataHex"] as? String, !raw.isEmpty, raw != "null" else { return nil }
            return raw
        }()
        let accessList = (json["accessList"] as? [[String: Any]]) ?? []

        // Mirrors the Android bridge: payload chainId must match the routing chain.
        precondition(
            chainId == chain.id,
            "Payload chainId \(chainId) does not match routing chain \(chain.displayName) (\(chain.id))"
        )

        let coinType = toCoinType(chain)
        guard let wallet = HDWallet(mnemonic: mnemonic, passphrase: "") else {
            fatalError("HDWallet: invalid mnemonic")
        }
        let privateKey = wallet.getKeyForCoin(coin: coinType)

        var transfer = EthereumTransaction.Transfer()
        transfer.amount = decimalToData(valueWei)
        if let hex = dataHex {
            transfer.data = Data(hexString: hex) ?? Data()
        }

        var ethTx = EthereumTransaction()
        ethTx.transfer = transfer

        var input = EthereumSigningInput()
        input.txMode = .enveloped
        input.chainID = int64ToData(chainId)
        input.nonce = int64ToData(nonce)
        input.maxFeePerGas = decimalToData(maxFeePerGasWei)
        input.maxInclusionFeePerGas = decimalToData(maxPriorityFeePerGasWei)
        input.gasLimit = decimalToData(gasLimit)
        input.toAddress = to
        input.privateKey = privateKey.data
        input.transaction = ethTx

        input.accessList = accessList.compactMap { entry -> EthereumAccess? in
            guard let address = entry["address"] as? String else { return nil }
            var access = EthereumAccess()
            access.address = address
            let keys = (entry["storageKeys"] as? [String]) ?? []
            access.storedKeys = keys.map { Data(hexString: $0) ?? Data() }
            return access
        }

        guard let output: EthereumSigningOutput =
                try? AnySigner.sign(input: input, coin: coinType) else {
            fatalError("EIP-1559 signing failed")
        }
        precondition(
            output.error == .ok,
            "EIP-1559 signing failed: \(output.error) – \(output.errorMessage)"
        )
        return "0x" + output.encoded.hexString
    }

    // MARK: - Helpers

    private func toCoinType(_ chain: SupportedChain) -> CoinType {
        switch chain {
        case SupportedChain.polygon: return .polygon
        case SupportedChain.bnbsmartchain: return .smartChain
        default: return .ethereum
        }
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

