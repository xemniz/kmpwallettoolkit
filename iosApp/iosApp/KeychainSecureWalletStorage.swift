import Foundation
import Security
import SampleCompose

/// Keychain-backed mnemonic storage.
///
/// Uses a generic password item with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`
/// so the secret is available to the app in the background but cannot migrate
/// off-device via iCloud Keychain. Items are scoped by a constant service
/// + account pair; overwrites use `SecItemUpdate` with `SecItemAdd` fallback.
///
/// CLAUDE.md §4.1: never log the stored value; `description` is redacted.
final class KeychainSecureWalletStorage: NSObject, SecureWalletStorage {

    private static let service = "xyz.wallet.toolkit.sample"
    private static let account = "mnemonic"

    private var baseQuery: [CFString: Any] {
        return [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: Self.service,
            kSecAttrAccount: Self.account,
        ]
    }

    func save(mnemonic: String) {
        guard let data = mnemonic.data(using: .utf8) else { return }

        let updateAttrs: [CFString: Any] = [kSecValueData: data]
        let updateStatus = SecItemUpdate(baseQuery as CFDictionary, updateAttrs as CFDictionary)

        if updateStatus == errSecItemNotFound {
            var addQuery = baseQuery
            addQuery[kSecValueData] = data
            addQuery[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(addQuery as CFDictionary, nil)
        }
    }

    func load() -> String? {
        var query = baseQuery
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func clear() {
        SecItemDelete(baseQuery as CFDictionary)
    }

    override var description: String { "KeychainSecureWalletStorage" }
}
