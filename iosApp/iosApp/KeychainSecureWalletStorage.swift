import Foundation
import Security
import SampleCompose

/// Device-only Keychain storage. Signing out removes the mnemonic but preserves unfinished operations.
final class KeychainSecureWalletStorage: NSObject, SecureWalletStorage, SecureOperationStorage {
    private static let service = "xyz.wallet.toolkit.sample"
    private static let mnemonicAccount = "mnemonic"
    private static let journalAccount = "unfinished-operations-v1"

    private(set) var journalReadFailed = false

    private func query(account: String) -> [CFString: Any] {
        return [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: Self.service,
            kSecAttrAccount: account,
        ]
    }

    private func saveValue(_ value: String, account: String) -> Bool {
        guard let data = value.data(using: .utf8) else { return false }
        let baseQuery = query(account: account)
        let updateAttrs: [CFString: Any] = [kSecValueData: data]
        let updateStatus = SecItemUpdate(baseQuery as CFDictionary, updateAttrs as CFDictionary)

        if updateStatus == errSecSuccess { return true }
        guard updateStatus == errSecItemNotFound else { return false }

        var addQuery = baseQuery
        addQuery[kSecValueData] = data
        addQuery[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(addQuery as CFDictionary, nil) == errSecSuccess
    }

    private func loadValue(account: String) -> (value: String?, failed: Bool) {
        var readQuery = query(account: account)
        readQuery[kSecReturnData] = true
        readQuery[kSecMatchLimit] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(readQuery as CFDictionary, &result)
        if status == errSecItemNotFound { return (nil, false) }
        guard status == errSecSuccess, let data = result as? Data,
              let value = String(data: data, encoding: .utf8) else { return (nil, true) }
        return (value, false)
    }

    func save(mnemonic: String) -> Bool {
        return saveValue(mnemonic, account: Self.mnemonicAccount)
    }

    func load() -> String? {
        return loadValue(account: Self.mnemonicAccount).value
    }

    func clear() -> Bool {
        let status = SecItemDelete(query(account: Self.mnemonicAccount) as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    func loadJournal() -> String? {
        let result = loadValue(account: Self.journalAccount)
        journalReadFailed = result.failed
        return result.value
    }

    func saveJournal(serialized: String) -> Bool {
        return saveValue(serialized, account: Self.journalAccount)
    }

    override var description: String { "KeychainSecureWalletStorage" }
}
