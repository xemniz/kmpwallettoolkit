# Run: parallel-GH — specs G & H — 2026-04-20

**Phase:** follow-up run after parallel-CEF. The four "leftover" follow-ups identified in the CEF wrap.
**Task(s):**
- **Scaffolding tweaks** (main session, not an implementer task):
  - `implementer.md`: "commit per phase" → "commit per *artifact-producing* phase"; no more `--allow-empty` commits for validation phases.
  - `crypto-reviewer.md`: new checklist item D.17 — independently revert-and-reproduce any implementer claim of "pre-existing red tests."
- **Code follow-ups** through the full pipeline:
  - **G** — align iOS Trust Wallet Core error contract with the `WalletTest.kt` JVM-green contract, closing the 2 pre-existing iOS failures that hung over the Phase-4 reviews.
  - **H** — drop the `json` parameter on `Eip1559Transaction.toSigningPayload` per reviewer A's Phase-3 should-fix; eliminate the silent `encodeDefaults = false` bypass.

**Agent config:** planner → implementer → crypto-reviewer per task, parallel across G and H.
**Parallelism:** G and H are file-disjoint (wallet-core iOS vs wallet-evm). Two-way parallel at every stage.
**Worktrees:**
- G = `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit-G` on `task/g-ios-contract` (removed after merge)
- H = `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit-H` on `task/h-eip1559-api` (removed after merge)

## Token usage

| Agent             | Tokens  | Notes |
|-------------------|---------|-------|
| planner G         | 40,085  | 7 tool uses. Flagged iOS-test-runner skipping risk, process-global adapter flake risk, message-substring-as-contract. |
| planner H         | 37,306  | 6 tool uses. Explicit confirmation golden vector does not drift. |
| implementer G     | 68,572  | 37 tool uses. 2 commits. Made the judgment call to skip optional Phase 3 because `TrustWalletCoreRuntimeTest.kt` already exists on main. |
| implementer H     | 62,247  | 26 tool uses. 2 commits. Grepped every `toSigningPayload(` call site to confirm no external caller passed a custom `Json`. |
| crypto-reviewer G | 54,169  | 24 tool uses. **Executed the inverse of D.17** — reverted iOS bridge files to main, reproduced the 2 WalletTest failures, restored G, confirmed 0 failures. |
| crypto-reviewer H | 40,452  | 14 tool uses. Verified three files byte-identical via `git diff | wc -c = 0`. |
| **Subagent total**| **302,831** | |

## Interventions

From the main session's perspective — scaffolding changes and orchestration moves, not mid-agent corrections.

| # | Stage | Why I stepped in | Category |
|---|-------|------------------|----------|
| 1 | Pre-flight | Scaffolding edits directly in main session (implementer.md, crypto-reviewer.md). These files are in CLAUDE.md §7's "never modify by implementer" list, so the orchestrator handles them. One commit. | scaffolding-tightening |
| 2 | Pre-flight | Diagnosed the iOS `WalletTest` failures myself (read the test, the JVM stub, the iOS bridge, traced the message mismatch) to write spec G concretely, rather than writing a vague "diagnose and fix" spec. | spec-diagnosis |
| — | During agent runs | None. Six subagent launches, zero mid-run corrections. | — |

## Rework rate

Pre-human-review:

- Lines changed by agents (G + H cumulative):
  - G: `2 files, +12 / -7` (iOS bridge + commonMain adapter)
  - H: `2 files, +69 / -2` (EvmSigningPayload.kt + new invariance test)
  - **Total: 4 files, +81 / -9.**
- Lines subsequently changed by me: **0**.
- **Rework rate: 0 / 81 ≈ 0%.**

## Failure modes observed

### What the new scaffolding caught (and what it didn't)
- **D.17 (reviewer revert-and-reproduce) worked as intended.** G's reviewer reset the iOS bridge files to main, reproduced exactly the 2 `WalletTest` failures the implementer claimed were the gate, restored G's state, confirmed green. This is the closed-loop proof that the Phase-4 "pre-existing" claim wasn't gaslighting. If this check had ever disagreed, we'd have discovered a regression hiding behind a "not my fault" shrug.
- **The commit-per-artifact-phase rule** held on both branches. G committed Phase 1 + Phase 2 (artifact phases), skipped Phase 0 (baseline validation) and Phase 4 (verification). H committed Phase 1 + Phase 2, skipped Phase 3 (verification). No `--allow-empty` commits.
- **What the scaffolding still doesn't catch**: the "skip an optional plan phase because a pre-existing file conflicts with the plan's 'new file' constraint" decision (implementer G, Phase 3). The implementer's judgment was correct — adding content to the pre-existing `TrustWalletCoreRuntimeTest.kt` would have exceeded scope; writing a second file with overlapping assertions would have been wasteful. But this is an unprompted scope-narrowing decision that the reviewer should validate. G's reviewer did validate it (E.17 PASS, criterion 6 skipped-but-justified). Worth noting as a pattern: implementers will sometimes discover that a plan phase is obsolete; the reviewer catches drift in both directions.

### Classic failure modes — rechecked
- **Platform code leaking into commonMain** — didn't happen. `TrustWalletCoreIosBridge.kt` changes use only `kotlin.buildString` and the existing `internal` surface. `compileKotlinIosX64` green.
- **Golden-vector drift** — didn't happen. H reviewer verified three `git diff | wc -c = 0` files (golden test, legacy Eip1559 test, legacy EVM test). Default-argument path was already routing through `Eip1559Json`, so dropping the parameter produces identical output.
- **Test weakening** — didn't happen. `WalletTest.kt` diff 0 bytes. `Eip1559SigningPayloadTest.kt` diff 0 bytes. Fix lived in the actual, not the test.
- **Hot-file race** — N/A, no hot file touched. (`TrustWalletCoreIosBridge.kt` is a commonMain file but not listed in §7; it probably should be, given it's the adapter-hook seam — flag for CLAUDE.md revision if this pattern recurs.)
- **KSP / daemon OOM across concurrent worktrees** — didn't happen again. Two-way parallel builds remained clean.

### New observation: implementer-initiated scope narrowing
Implementer G explicitly skipped the plan's optional Phase 3 and reported the decision and reasoning in its final message. The plan granted this as optional, so the action was in-bounds. What's notable is that a less-disciplined implementer could have either (a) created a redundant second test file to "honor the plan," or (b) silently ignored Phase 3 without reporting. G reported, which makes the skip auditable. The reviewer picked it up and validated it.

**Takeaway for the article:** "optional phases in a plan" are a subtle coordination device. They let the implementer exercise judgment while staying auditable. Not a generic-guide pattern; worth calling out.

## Success moments worth citing

- **D.17 full-loop trust closure.** Three-agent chain: implementer E (parallel-CEF) discovers a pre-existing failure and refuses to mask it; implementer G (parallel-GH) fixes it; reviewer G independently reproduces the *originally* failing state and confirms the fix restores green. The implementer never got to just say "trust me."
- **Zero tests weakened across three two-way parallel runs.** `WalletTest.kt`, `Eip1559SigningPayloadTest.kt`, `EvmSigningPayloadTest.kt`, `RpcClientTest.kt`, `Eip1559GoldenVectorTest.kt` all byte-identical since their creation. This is the single strongest signal that the scaffolding holds under crypto-sensitive pressure.
- **Scope narrowing audit trail.** G's implementer skipped an optional plan phase for a defensible reason and said so; G's reviewer validated and marked acceptance criterion 6 "skipped-but-justified." Auditable from the final messages alone.

## Retrospective (fill in after you've slept on it)

-
