# Run: parallel — D, T, S1–S6 sample-wallet showcase — 2026-04-21

**Phase:** parallel-DTS1-S6 (sequential waves: D → S1 → {T + S2 + S3 + S4 + S6 wave 1} → S5 wave 2 → SW-wireup inline)
**Task(s):** D (bridge-exhaustive), T (eip1559-sign), S1 (sample shell), S2 (create), S3 (import), S4 (home), S5 (send), S6 (tx-status), + inline App.kt wireup.
**Agent config:** `/spec` (architect + 6 parallel writers) + `/implement` pipeline (planner → implementer → crypto-reviewer) per task, run via `/implement` skill.
**Worktrees:** 7 total across the run (all cleaned up).
**Concurrency:** wave 1 = 5 parallel agents (T, S2, S3, S4, S6); wave 2 = 1 (S5 waited on T merge); D + S1 were serial warm-ups.

## Token usage

| Agent                        | Tokens   | Notes |
|------------------------------|----------|-------|
| Main session (orchestrator)  | ~unknown | pre-compaction context ran 350k+; compaction hit mid-run |
| `/spec` architect + 6 writers | ~60k | parallel writer pass for S1–S6 + D |
| planner × 8                  | ~400k    | roughly 40–60k per spec. S5 planned twice (pre-T STOP → post-T wire) |
| implementer × 8              | ~600k    | S1 on Android bridge surface was heaviest; T's protobuf mapping next |
| crypto-reviewer × 3 completed | ~120k | D + S1 + T. S2/S3/S4/S5/S6 reviewers hit session quota before returning |
| **Total**                    | ~1.2M+   | sample-compose build + test gates not counted |

## Interventions

Count every time I stepped in mid-run to course-correct, clarify, or unblock. Ambiguous = counts.

| # | Agent | Turn | Why I stepped in | Category |
|---|-------|------|------------------|----------|
| 1 | main | pre-flight | Spec set for sample-wallet would touch `wallet-core` bridge non-exhaustiveness (pre-existing E+F bug). Spun up spec D as a pre-cursor. | spec-ambiguity |
| 2 | main | pre-flight | Specs S2–S6 share `App.kt` — would race. Carved seam S1 to own App.kt + Route/Navigator + theme/ui/format/rpc; deferred full wire-up to a post-merge follow-up. | spec-ambiguity |
| 3 | main | S1 planner → impl | Found the `./gradlew :sample-compose:compileKotlinJvm` / `:compileDebugKotlinAndroid` tasks in the plan don't exist for this project. Corrected the impl prompt to `:sample-compose:assemble` + `:compileAndroidMain`. | conventions-miss |
| 4 | main | after S1 | Spec T's planner flagged that wallet-evm depends on wallet-core, not vice versa — can't put a wallet-evm extension that calls an internal wallet-core symbol. Chose plan option (b): method lives in wallet-core, wallet-evm has a thin `signEip1559Transaction` that is just a tiny type-targeted extension. | spec-ambiguity |
| 5 | main | T implementer | Added throwing default methods on `WalletEngine.signEip1559` and `TrustWalletCoreIosAdapter.signEip1559` so existing test fakes keep compiling without touching them. Deviation from plan; accepted. | conventions-miss |
| 6 | main | after wave-1 implementers | All 5 wave-1 reviewers (T, S2, S3, S4, S6) hit session quota — "resets 9pm Europe/Madrid". Ran inline orchestrator sanity (grep for Random, println, Log.*, GlobalScope, .message in the diff; verified `chain.toCoinType()` at the Android signing sites; revert-and-diff on test files the spec marked as must-not-change) and authorized merge. One T reviewer (the retry) did return later independently and returned a clean MERGE. | tool-failure |
| 7 | main | wave-2 S5 | S5 planner had run pre-T — its plan mandated STOP because the signing API didn't exist. After T merged, re-ran the planner against post-T main. | spec-ambiguity |
| 8 | main | post-S5 | Wave-3 App.kt wire-up (5 `when` branches) judged too trivial for full pipeline — did it inline, verified with `:sample-app:assembleDebug`. | other |

## Rework rate

Pre-user-review. Changes made by me (orchestrator) to implementer output before merge:

- Lines changed by agents (`git diff main~16..main --stat` — waves D + S1 + {T,S2,S3,S4,S6} + S5): **4839 inserted, 132 deleted across 65 files**
- Lines I rewrote at implementer-done: **0** (no post-implementer edits on any branch except the inline App.kt wire-up, which was a separate follow-up not a rework)
- **Rework rate:** 0 / 4839 = **0%** pre-review

Inline App.kt wire-up (10 lines added, 22 deleted) is wave-3 follow-up work, not rework of a prior implementer's output.

## Failure modes observed

### Failure 1: reviewer session quota exhaustion
**When:** wave-1 of 5 parallel implementers all completed; attempted to spawn 5 parallel crypto-reviewers.
**Agent:** all 5 reviewers returned with a session-quota error ("resets 9pm Europe/Madrid") before producing a verdict.
**What I did:** inline orchestrator sanity pass — for each branch: `git diff --name-only` for scope, grep the diff for `println|Log\.|kotlin.random|GlobalScope|contentEquals` against secret-adjacent contexts, revert-and-diff any test files the spec marked as must-not-change, confirm `chain.toCoinType()` routing on the one crypto-touching task. User approved inline sanity as merge-authorization.
**Root cause:** independent of the agent framework — parallel tool-session concurrency ceiling reached in the wrapping harness. Notable that ALL FIVE reviewers failed identically with the same quota message — suggests a single quota pool, not per-agent.

### Failure 2: stale plan surviving a dependency merge
**When:** wave-2 S5 implementer about to spawn.
**Agent:** N/A (caught before launching the implementer).
**What I did:** re-spawned the S5 planner against post-T main. The old plan was rewritten root-and-branch.
**Root cause:** `/implement` playbook planned everything at once in wave 0, but waves 1 & 2 were separated precisely because T had to merge first. Plans written before a dependency lands are data-stale the moment that dependency merges. Future runs should re-plan any wave-N task whose wave-(N-1) dependency touched a surface the plan references.

### Failure 3 (non-failure): prediction that didn't materialize
**Predicted modes from earlier runs:** platform-leak (`java.security` in commonMain), golden-vector drift, test weakening, hot-file race, KSP corruption, Gradle OOM, `wallet.core.jni.*` from commonMain. **Observed incidence: zero across 8 tasks.** The CLAUDE.md §4 + §7 guardrails plus the scope fence in the implementer prompt held end-to-end. Explicitly noteworthy because prior runs did see platform leaks and test weakening.

## Success moments worth citing in the article

- **T's implementer derived `coinType = chain.toCoinType()` at the Android signing site on the first try**, despite the legacy `signEvmTransaction` at line 157 of the same file still hardcoding `CoinType.ETHEREUM`. The plan and spec both called this out as a tripwire; the implementer respected it without re-opening the decision. The independent reviewer (retry) confirmed it line-by-line.
- **D's `ChainCoinTypeMappingTest` tripwire** is a pattern worth lifting: iterate an enum, assert every entry is covered by a test-side expected-map. Future chains can't be added without updating both the bridge and the test — exactly the failure mode E+F slipped through.
- **File-disjoint decomposition held.** 6 specs (D + S1–S6) wrote zero-overlap file lists; zero merge conflicts across 8 merges. The `/spec` command's disjointness-verifier is load-bearing.
- **The stale-plan catch.** S5's planner refused to invent a workaround when its spec said STOP. Re-planning against post-T main produced a clean, full-flow plan without a single intervention. This is the right behavior — a planner that silently papers over a missing dependency would have shipped a broken flow.
- **SendState's hand-rolled `toString()` = "SendState(redacted)"** + explicit "not a `data class`" comment. Implementer inlined CLAUDE.md §4.1 without being told line-by-line. Prompt-engineering-as-policy worked.

## Retrospective (fill in after sleeping on it)

- _pending_
