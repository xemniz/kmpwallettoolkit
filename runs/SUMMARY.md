# Experiment summary — parallel Claude Code agents on a KMP crypto library

Three runs, seven merged tasks, zero mid-run interventions, 0% rework rate pre-user-review. This file is a bridge from the raw run logs to the article; the detail lives in the per-run files.

- [parallel-AB](parallel-AB-20260420.md) — 2 tasks, clean boundaries, baseline
- [parallel-CEF](parallel-CEF-20260420.md) — 3 tasks, hot-file contention stress test
- [parallel-GH](parallel-GH-20260420.md) — 2 follow-ups, D.17 trust closure

## What the scaffolding is made of

- [CLAUDE.md](../CLAUDE.md) — KMP source-set rules, crypto hygiene, per-module test commands, hot-file list.
- [.claude/agents/](../.claude/agents/) — planner, implementer (worktree-isolated), crypto-reviewer.
- [.claude/commands/implement.md](../.claude/commands/implement.md) — one-liner orchestration (`/implement A` or `/implement A B C`).
- [specs/](../specs/) — one markdown file per task. `##Module(s) touched`, `## Files expected to change`, `## Acceptance criteria`, `## Non-goals`.
- [runs/](.) — per-run logs filled during execution.

## Results at a glance

| Run | Tasks | Verdicts | Rework | Orchestrator interventions | Subagent tokens |
|---|---|---|---|---|---|
| parallel-AB | 2 (A EIP-1559, B RPC methods) | 2 × MERGE | 0% | 2 (pre-flight: planner.md frontmatter fix, manual worktree workaround) | 316k |
| parallel-CEF | 3 (C BIP-39, E Optimism, F BnbSmartChain) | 3 × MERGE | 0% | 3 (hot-file authorization + serialization + acceptance-of-pre-existing-iOS-fails) | 436k |
| parallel-GH | 2 (G iOS contract, H Eip1559 API) | 2 × MERGE | 0% | 2 (scaffolding tweaks pre-flight + iOS failure diagnosis to ground spec G) | 303k |

7 merges on main. 0 mid-run interventions across 18 subagent launches.

## What generic parallel-agent guides miss — empirically

1. **kotlinx-serialization's `encodeDefaults = false` as a silent-drift trap.** Planner A caught this before implementer hit it. Without the planner's risk-surface step, the implementer would have frozen a wrong-shaped golden JSON and the test would lock it in as canonical.
2. **Hot-file contention is an orchestrator cost, not an agent cost.** Stop-and-raise rule (both E and F planners produced this correctly) is half the protocol. The other half — fast-forward the second worktree onto post-first-merge main before running its implementer — is an orchestrator move with no agent-side analogue. Missing this guarantees either stale tests or merge conflicts.
3. **"Pre-existing red test" is a trust vector that has to be closed independently.** Implementer E refused to mask two iOS failures and documented them as inherited. Reviewer E reproduced them by reverting E's changes. In parallel-GH, reviewer G ran the inverse — reverted G's fix, reproduced the *expected* failures, restored, confirmed green. This is codified in `.claude/agents/crypto-reviewer.md` D.17.
4. **Defaults in a public crypto API are a silent footgun.** Reviewer A flagged a should-fix on `Eip1559Transaction.toSigningPayload(json: Json = Eip1559Json)` — a caller passing a custom `Json` without `encodeDefaults = true` would silently produce a wrong signing input. Planner and implementer both missed it; reviewer caught it. Fixed in task H.
5. **Commit-per-phase needs "artifact-producing" qualifier.** Implementer C committed an `--allow-empty` Phase 4 to honor the rule literally. History noise. Rule rewritten in `.claude/agents/implementer.md` before parallel-GH.
6. **Optional plan phases let implementers exercise judgment without going rogue.** Implementer G correctly skipped an optional Phase 3 because a pre-existing test file conflicted with the plan's "new file" constraint. The final message reported the skip; reviewer G validated it. Auditable without mid-run intervention.

## What didn't go wrong — and shouldn't be hand-waved

The PDF's predicted failure modes and whether they materialized:

| PDF prediction | Materialized? | Note |
|---|---|---|
| Platform code leaking into commonMain | No | Every touched commonMain file pure `kotlin.*` / `kotlinx.*` |
| `java.security` APIs in commonMain | No | Tasks didn't need entropy primitives |
| Two agents racing on `libs.versions.toml` | **Untested** | No task required a new dep across all three runs |
| KSP / Kotlin compiler cache corruption across worktrees | No | Three concurrent Gradle invocations clean |
| Tests "passing" by weakened assertions | No (strongly) | Inverse happened — implementer E *added* constraint |
| `Random` instead of `SecureRandom` in crypto | N/A | No randomness introduced |
| Gradle daemon OOM at multi-worktree build | No | Hardware = single M-series Mac; sample size 1 |

**Caveat on absence-as-data:** single hardware, modest concurrent load. The KSP / daemon failures may be real at N=4+ or on under-resourced hosts. The `libs.versions.toml` race is untested because we didn't pick tasks that needed deps — that's a gap in the experimental design, not evidence of safety.

## Token economics

- Planner: ~35–42k per task.
- Implementer: ~50–72k per task. Range reflects task complexity (BIP-39 paste: 60k; Chain enum append: 50k; iOS bridge rewrite: 69k).
- Reviewer: ~38–54k per task.
- **Full pipeline per task: ~130–165k subagent tokens.**
- Parallelism is nearly free on token cost (per-task budget unchanged); the saving is wall clock, not tokens.

## What to tune for run 4 (if there is one)

- Design at least one task that forces a `libs.versions.toml` or `settings.gradle.kts` contention to close the untested row.
- Try N=4+ to pressure the Gradle daemon on a weaker host.
- Add `--quick` exercise tasks (doc fixes, renames) to validate the skip-planner path.
- Consider an `/newspec` slash command that drafts specs from free-text — only if writing specs becomes the next bottleneck.

## When this is worth it (honest)

- **Worth it** when tasks are file-disjoint and acceptance criteria are testable (unit tests, compile-gates, golden vectors). The parallelism buys wall clock with no token penalty.
- **Worth it** specifically on crypto-adjacent code where the reviewer stage earns its cost. Reviewer A caught the caller-`Json` bypass; reviewer G closed the pre-existing-failure trust vector. Neither would have been caught by the implementer alone.
- **Not worth it** on tasks where the spec is vague. Agents fail soft on vague crypto specs, which is the most dangerous failure mode. If you can't write a concrete spec (files touched, acceptance criteria), don't run the pipeline.
- **Not worth it** on tasks that are single-file, single-line, with no crypto or hot-file exposure — the `--quick` path exists but overhead is still real.

## Article shape (from the PDF, keyed to this dataset)

1. **Setup** — what I built, why KMP crypto, what I measured → CLAUDE.md + subagents + specs + this repo's state at commit `972a0ce`.
2. **What generic guides promise** — one paragraph.
3. **What I actually did** — scaffolding excerpts, directory layout, the `/implement` command.
4. **Results** — the three-run table above.
5. **What broke** — the six numbered failure modes above, each with a commit SHA and a paragraph of agent output from the corresponding run log.
6. **What I'd change** — the four tuning items above.
7. **When this is worth it vs. not** — the honest-take section above.

Repo HEAD is `972a0ce`. Every artifact needed for the article is in-tree and committed.
