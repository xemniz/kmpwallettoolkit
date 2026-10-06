These fixtures use the public BIP-39 test phrase `abandon` eleven times followed by `about`, an empty passphrase, and Ethereum derivation path `m/44'/60'/0'/0/0`.

Legacy and type-2 signed envelopes, and their Keccak-256 transaction hashes, were independently reproduced with **ethers 6.15.0**. The existing signed-byte expectations were unchanged. Trust Wallet Core performs the production signing and hashing; ethers is only an external reference and is not a toolkit dependency.

Reference API: https://docs.ethers.org/v6/api/wallet/

To reproduce in an empty temporary directory:

```sh
npm install --no-save --ignore-scripts ethers@6.15.0
```

```js
const { Wallet, keccak256 } = require("ethers");
(async () => {
    const wallet = Wallet.fromPhrase(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    );
    const shared = {
        chainId: 1,
        to: "0x3535353535353535353535353535353535353535",
        value: 1000000000000000000n,
        gasLimit: 21000,
        nonce: 0,
    };
    const legacy = await wallet.signTransaction({
        ...shared, type: 0, gasPrice: 20000000000n,
    });
    const type2 = await wallet.signTransaction({
        ...shared, type: 2,
        maxFeePerGas: 20000000000n,
        maxPriorityFeePerGas: 1000000000n,
        accessList: [],
    });
    console.log({ legacy, legacyHash: keccak256(legacy), type2, type2Hash: keccak256(type2) });
})();
```

Expected hashes:

- Legacy: `0x734eb79981ee1c1819cd3414c8f2b07a0a4fad73bbb7e6cebc348318acf1231d`
- Type-2: `0x7b6daebe9bdcf98b876366bedeff2388746e6bb38cb2f4a54d460bafc7e17bba`

The common fixture tests run native signing and hashing on iOS and Android device tests. JVM tests use the explicitly unavailable native-runtime stub and therefore do not verify cryptographic vectors.
