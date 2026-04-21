# Run: parallel-CEF — specs C, E, F — 2026-04-20

**Phase:** parallel-CEF (Phase 4 per the PDF; scale-up after parallel-AB passed)
**Task(s):**
- C: BIP-39 English wordlist validation in `wallet-utils` (safe ballast, zero hot files)
- E: Register `SupportedChain.Optimism` (wallet-core; hot-file edit on `Chain.kt`)
- F: Register `SupportedChain.BnbSmartChain` (wallet-core; hot-file edit on `Chain.kt` — same file as E)
**Agent config:** full pipeline — planner → implementer → crypto-reviewer — per task
**Parallelism shape:**
- **Planner stage:** all three planners in parallel (read-only, no contention).
- **Implementer stage:** C and E in parallel (independent modules). F **serialized** after E merged: F's branch was fast-forwarded to post-E main before F's implementer ran, so F saw Optimism already present. This is the orchestration pattern for hot-file contention.
- **Reviewer stage:** C and E reviewed in parallel, F reviewed after F's implementer returned.

**Worktrees:**
- C = `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit-C` on `task/c-bip39` (removed after merge)
- E = `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit-E` on `task/e-optimism` (removed after merge)
- F = `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit-F` on `task/f-bnb` (removed after merge)

## Token usage

| Agent              | Tokens  | Notes |
|--------------------|---------|-------|
| planner C          | 36,423  | 5 tool uses. Risks: hand-paste hazard, `Valid ≠ checksum-valid`, empty-phrase semantics chosen as `InvalidLength(0)`. |
| planner E          | 39,628  | 10 tool uses. Phase 1 = explicit stop-and-raise on `Chain.kt`. |
| planner F          | 37,443  | 8 tool uses. Phase 1 = explicit stop-and-raise on `Chain.kt`. |
| implementer C      | 59,753  | 32 tool uses. Pasted the 2048-word literal, §4.1 toString overrides + redaction test, §4.3 KDoc on wordlist. Phase 4 was an empty commit for the phase trail. |
| implementer E      | 72,426  | 45 tool uses. Discovered 2 pre-existing iOS `WalletTest` failures, verified by revert-and-reproduce; correctly did NOT touch `WalletTest.kt`. |
| implementer F      | 51,388  | 21 tool uses. Ran after E merged; saw Optimism in its base; added `BnbSmartChain` as 6th entry. |
| crypto-reviewer C  | 53,082  | 8 tool uses. Hand-counted the 2048-word literal as belt-and-braces (Phase 1 tests already assert this). |
| crypto-reviewer E  | 47,364  | 15 tool uses. Independently verified pre-existing iOS failures via `stash` + reset `Chain.kt` + re-run iOS tests. |
| crypto-reviewer F  | 38,275  | 13 tool uses. Re-raised the pre-existing-iOS-failures should-fix for the record. |
| **Subagent total** | **435,782** | |
| Main session       | (unmeasured) | Orchestration, hot-file authorization prompts, the serialization dance. |

## Interventions

Counted from the main session's perspective — moments I corrected, constrained, or pre-authorized an agent.

| # | Stage | Why I stepped in | Category |
|---|-------|------------------|----------|
| 1 | Implementer kickoff | Pre-authorized E's implementer to edit `Chain.kt` past the plan's Phase-1 stop-and-raise (CLAUDE.md §7 hot file) via the orchestrator-authorization clause in the prompt. Same pattern for F, post-E-merge. | hot-file-orchestration (new category — see note below) |
| 2 | Post-E implementer | Fast-forwarded F's worktree onto post-E main before spawning F's implementer. Without this, F would have written its test against a stale 4-chain enum and conflicted at merge time. | serialization-protocol |
| 3 | Pre-implementer-F launch | Adjusted F's implementer prompt to explicitly name the "same 2 pre-existing iOS failures" as acceptable (not blockers), so F didn't get blocked by an unfixable-in-scope red test gate. | spec-drift-absorbed |
| — | During agent runs | None. Nine subagent launches, zero mid-run corrections. | — |

**New category for this run: `hot-file-orchestration`.** The stop-and-raise rule is honored by the plan; the "raise" is absorbed by the orchestrator's authorization prompt rather than a separate user-approval round-trip. Document this pattern in the article — it's the practical resolution of CLAUDE.md §7.

## Rework rate

Pre-human-review numbers:

- Lines changed by agents (cumulative for C + E + F):
  - C: `2 files, 491 insertions` (wallet-utils/Bip39Wordlist + test)
  - E: `2 files, 59 insertions` (wallet-core/Chain.kt +1 line, ChainRegistryTest new)
  - F: `2 files, 40 insertions` (wallet-core/Chain.kt +1 line, BnbSmartChainTest new)
  - **Total: 6 files, 590 insertions, 0 deletions.**
- Lines subsequently changed by me: **0**. Pre-review.
- **Rework rate: 0 / 590 ≈ 0%.**

Pending: user's own diff review may raise this number.

## Failure modes observed

### The PDF's predicted failure modes — recheck at 3-way parallelism
- **Platform code leaking into commonMain** — did not happen. Every touched commonMain file is pure Kotlin + `kotlinx.*`.
- **`java.security` APIs in commonMain** — did not happen (tasks did not need entropy primitives).
- **Two agents racing on `libs.versions.toml`** — **not triggered on this run** because no task required a new dep. Still untested under three-way pressure.
- **KSP / Kotlin compiler cache corruption across worktrees** — did not happen. Three concurrent Gradle invocations (C + E in one wave, F in a second) shared `~/.gradle` with zero collisions observed.
- **Tests "passing" by weakened assertions** — did not happen; the opposite happened. Implementer E discovered broken iOS tests in `WalletTest.kt`, correctly verified they were pre-existing, and did NOT touch them per CLAUDE.md §4 rule 6. This is a rare-in-LLM-reports instance of an agent *adding* constraint rather than removing it.
- **Random instead of SecureRandom in crypto code** — N/A, no randomness touched.
- **Gradle daemon OOM when multiple worktrees build simultaneously** — did not happen. Cold first-run compile: ~60s (wallet-utils on C) / ~40s (wallet-core on E); subsequent runs 10–25s. Sample size is 1.

### Hot-file contention (the test this run was designed for)
**The stop-and-raise rule is only half the protocol.** Both E's and F's planners correctly placed "Phase 1 = stop and raise on `Chain.kt`" at the top of their plans. But that only prevents two agents from silently overwriting each other — it does NOT solve the temporal question of *which one goes first*. The remaining half is orchestrator-side:

1. Authorize one of the two (E first, arbitrary choice).
2. Let it run, review, merge.
3. Fast-forward the other's worktree onto the new main (so its test is written against the post-merge state, not the pre-merge state).
4. Authorize the second with a prompt note that the first has landed.
5. Let it run, review, merge.

Steps 3 and 4 are invisible in the agent prompts but essential. **Without step 3, F's test would have asserted `size == 5` (pre-E-merge view) and failed the moment F merged alongside E.** This is the specific operational cost of parallel agents on a hot file, not captured by the PDF or by generic parallel-agent guides.

### New failure mode: "pre-existing red tests become blockers"
The wallet-core iOS targets have two `WalletTest` tests that fail on iOS (sim and X64) due to the `TrustWalletCoreIosAdapter` runtime not being installed in the test environment. These failures predate Phase 4. Both E's and F's implementers hit them.

The *right* behavior from an implementer (don't weaken, don't fix out-of-scope) is impossible to distinguish from the *wrong* behavior (silently give up on the iOS gate) unless the failure cause is diagnosed. Both implementers did the right thing — one with explicit revert-and-reproduce verification, the other by citing its peer's verification. But this is a trust-dependent behavior that scales badly: if a reviewer blindly accepts "pre-existing, not my problem" without verification, an implementer can hide a regression.

**Mitigation for future runs:** reviewers must independently revert-and-reproduce any red tests the implementer claims are pre-existing. Reviewer E did this explicitly and the pattern is worth codifying in `crypto-reviewer.md`.

### Empty Phase-4 commit (implementer C)
Implementer C committed `3a88c5f wallet-utils: Phase 4 portability sanity check — all gates green` with `--allow-empty`, honoring the plan's "one commit per phase" rule literally even though Phase 4 has no code deliverable. Defensible interpretation, but clutters the history with a commit that has no artifact.

**Takeaway:** the "one commit per phase" rule in `implementer.md` should say "one commit per phase *that produces artifacts*" — or accept that validation-only phases are a legitimate no-commit.

## Success moments worth citing

- **Three-way parallel planners, zero token wasted on scope drift.** All three plans came back scoped correctly, with E and F both correctly gating themselves on user authorization at Phase 1.
- **Implementer E's pre-existing-failure handling.** This is the cleanest demonstration of "don't weaken assertions" in the whole run. The implementer could have trivially marked the tests `@Ignore`, added an `@Suppress`, or just committed and ignored the iOS gate. Instead it ran the revert-and-reproduce dance manually and flagged the issue for out-of-scope tracking.
- **Reviewer E's independent verification.** Reproduced the pre-existing failure pattern on main, then restored E's state cleanly — this is what keeps the "pre-existing" claim trustworthy.
- **Serialization protocol worked.** F's branch fast-forwarded onto post-E main, F's test was written against the post-merge state, F's merge produced zero conflicts on `Chain.kt` even though E and F both edited the same two lines of the enum. The topology is visible in `git log --graph`: E's merge comes before F's, F's merge has E's commit in its base.
- **No Gradle / KSP corruption under two-way or one-way parallel builds.** The PDF's most-feared failure mode did not materialize on this hardware. Caveat: single M-series Mac, modest concurrent load.

## Retrospective (fill in after you've slept on it)

-
