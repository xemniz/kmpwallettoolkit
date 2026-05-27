# Release Checklist

Use this checklist before calling a milestone usable by app developers.

## Scope

- Public API examples in `README.md` compile against the current modules.
- `docs/STARTER_GUIDE.md` matches the sample app's supported flow.
- Supported targets and chains are documented.
- Any work-in-progress feature is labeled as such.

## Security

- No mnemonics, private keys, seeds, or raw signing secrets are logged.
- Host apps store mnemonics only through platform secure storage.
- Transaction nonces are fetched from RPC and are not generated locally.
- Signing serialization changes have golden-vector or drift tests.
- New crypto dependencies have explicit design approval.

## Verification

```bash
./gradlew :wallet-utils:allTests
./gradlew :wallet-core:allTests
./gradlew :wallet-evm:allTests
./gradlew :wallet-rpc:allTests
./gradlew :sample-compose:allTests :sample-app:assembleDebug
```

## Manual Sample Pass

- Create wallet.
- Restart and confirm the wallet rehydrates from secure storage.
- Import wallet.
- Open home with no optional API credentials configured.
- Validate send form errors for empty recipient, invalid recipient, and missing RPC config.
- Submit a testnet transaction only with test funds and confirm the pending/status states.
