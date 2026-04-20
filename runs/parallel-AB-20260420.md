# Run: parallel-AB — specs A & B — 2026-04-20

**Phase:** parallel-AB (Phase 3 per the PDF; baseline skipped by user decision)
**Task(s):** A (EIP-1559 tx type in wallet-evm), B (getTransactionReceipt/ethCall/getCode in wallet-rpc)
**Agent config:** full pipeline per task — planner → implementer → crypto-reviewer, parallel across A and B at each stage
**Worktrees:**
- A = `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit-A` on `task/a-eip1559`
- B = `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit-B` on `task/b-rpc-methods`

**Concurrency:** two background `Agent` subagent invocations per stage, launched in a single parent message. Main session orchestrates stage boundaries (plans committed to main before implementers start; implementers commit to their branches before reviewers read).

## Token usage

| Agent              | Tokens  | Notes |
|--------------------|---------|-------|
| planner A          | 40,944  | 10 tool uses. Surfaced `encodeDefaults` risk before implementer hit it. |
| planner B          | 41,562  | 8 tool uses. Surfaced `JsonNull` vs Kotlin-null distinction, scoped-lenient-Json caching. |
| implementer A      | 62,209  | 30 tool uses. Followed planner's escape hatch for `encodeDefaults`. |
| implementer B      | 68,271  | 38 tool uses. Ran drift-guard integrity check (temp-remove tolerance, verify fail, restore) before committing Phase 2. |
| crypto-reviewer A  | 51,134  | 20 tool uses. Caught a should-fix the implementer didn't flag. |
| crypto-reviewer B  | 51,405  | 19 tool uses. Re-verified `lenientJson` scope + `RpcClientTest.kt` byte-identical. |
| **Subagent total** | **315,525** | |
| Main session       | (unmeasured) | Orchestration: scaffolding, worktree setup, prompts, log. |

## Interventions

Counted from the main session's perspective — moments I corrected or constrained an agent mid-run, or caught a scaffolding bug before launch.

| # | Stage | Why I stepped in | Category |
|---|-------|------------------|----------|
| 1 | Pre-kickoff | `planner.md` frontmatter declared `tools: Glob, Grep, Read, WebFetch, WebSearch` but the planner's instructions said to write `specs/<name>.plan.md`. Added Write to the tool list in a dedicated commit before launching planners. | conventions-miss (my own scaffolding) |
| 2 | Pre-kickoff | `Agent(isolation: "worktree")` refused with "not a git repository" because the session's env snapshot was taken before `git init`. Manually created worktrees via `git worktree add` and dropped the isolation parameter; briefed each agent on its worktree path explicitly. | tool-failure |
| — | During agent runs | None. Both planners, both implementers, both reviewers ran fire-and-forget in the background. Zero mid-run corrections. | — |

Categories used: `spec-ambiguity`, `conventions-miss`, `crypto-rule-violation`, `test-weakened`, `platform-leak`, `wrong-module`, `dependency-drift`, `daemon-issue`, `tool-failure`, `other`.

## Rework rate

Measured at the reviewer-declared-done boundary, before any human review pass.

- Lines changed by agents (implementer A + B combined, `git diff --stat main..<branch>`):
  - A: `4 files, 204 insertions`.
  - B: `4 files, 333 insertions, 2 deletions` (the 2 deletions are the refactored private `call` signature; no behavior change).
  - **Total: 8 files, 537 insertions, 2 deletions.**
- Lines subsequently changed by me: **0**. No human rework on the branches.
- **Rework rate: 0 / 537 ≈ 0%.**

Caveat: this is the pre-human-review number. The user has not done their own pass over the diffs yet; final rework will be filled in after that pass.

## Failure modes observed

### The PDF's predicted failure modes that did NOT materialize
(Recording absences because they're also data. The PDF's watch-list, rechecked against the diffs.)

- **Platform code leaking into commonMain** — didn't happen. No `java.*` / `android.*` / `androidx.*` in any touched commonMain file.
- **`java.security` APIs in commonMain** — N/A, neither task was crypto-primitive; delegation to Trust Wallet Core was untouched.
- **Two agents racing on `libs.versions.toml`** — didn't happen. Neither planner proposed touching it; neither implementer touched it. Scope fence held.
- **KSP / Kotlin compiler cache corruption across worktrees** — didn't happen. Both agents ran `:<module>:allTests` concurrently without triggering it. Sample size is 1, hardware is a single M-series Mac — don't generalize.
- **Tests "passing" by weakened assertions** — didn't happen. Reviewer A re-ran `:wallet-evm:allTests` with `--rerun-tasks`; reviewer B confirmed `RpcClientTest.kt` is byte-for-byte unchanged vs `main`.
- **Random instead of SecureRandom in crypto code** — N/A, no entropy generated.
- **Gradle daemon OOM when multiple worktrees build simultaneously** — didn't happen on this run. Cold first-run compile was ~33s (wallet-evm) / ~20s (wallet-rpc). The two worktrees share `~/.gradle` dependency cache but have per-worktree `.gradle/` project caches; no collision was observed.

### Actual friction (the article's material)

1. **Scaffolding bug caught pre-launch** — planner.md's frontmatter declared `tools: Glob, Grep, Read, WebFetch, WebSearch` but its body said "Write the plan to specs/<name>.plan.md." This is the kind of thing a generic guide wouldn't predict and that would have caused the planners to silently not produce their output files. Fix: added `Write` to the tool list and committed before launching. **Takeaway:** subagent tool declarations and their instruction bodies drift out of sync trivially; lint them or they will bite.

2. **`isolation: worktree` parameter refused on a repo that was git-init'd mid-session.** The Claude Code harness captures "is this a git repo" at session start; subsequent `git init` in the same session doesn't update the flag. Workaround: manual `git worktree add` + brief each agent on its path. **Takeaway:** if you're setting up from scratch, do the `git init` before starting your orchestration session, not inside it.

3. **kotlinx-serialization's `encodeDefaults = false` is a silent-drift trap for golden vectors.** The plan-A agent caught this up front in its risk section; without that catch, the implementer would have generated JSON missing `valueWei`, `accessList`, and `dataHex`, committed it as the golden string, and the test would have locked in a wrong canonical form. **Takeaway:** the planner stage earned its cost on task A specifically by surfacing this before implementation.

4. **Drift-guard test integrity is a separate discipline from "test passes."** On spec B, the `blobGasUsed` unknown-field test would pass if the parser never actually received the field (e.g. wrong JSON body) or if the tolerance were applied too broadly. The implementer manually verified — temporarily removed the tolerance, re-ran just that test, confirmed it failed, restored the tolerance, re-ran. **Takeaway:** the crypto-reviewer's checklist item B.8 ("drift guard is non-trivially satisfied") is a real thing, not boilerplate; the reviewer verified this independently.

5. **Caller-injected `Json` bypasses scoped defaults** (Reviewer A's should-fix). On `Eip1559Transaction.toSigningPayload(json: Json = Json)`, the implementer's private `Eip1559Json = Json { encodeDefaults = true }` is used only when the default argument is taken. A caller passing a custom `Json` instance without `encodeDefaults = true` would silently produce a different signing input. Non-blocking here because no caller does that yet, but if this API ships unchanged, the first custom-`Json` caller has a quiet footgun. **Takeaway:** defaults-in-public-crypto-API is a subtle trap that gets past planner and implementer; this is where the reviewer earns its cost.

## Success moments worth citing

- **Planner → implementer handoff on the `encodeDefaults` risk** (friction #3 above): planner flagged the issue in advance; implementer read the plan's risk section and took the pre-authorized escape hatch rather than improvising. Clean handoff — exactly what a three-stage pipeline is supposed to produce.
- **Zero mid-run interventions** across six subagent launches. Every agent's final message was actionable and accurate; no clarifications, no re-prompts, no rescues.
- **Reviewer surfaced a blind-spot the implementer + planner missed** (friction #5). This is the best evidence for the three-stage pipeline being worth its token cost on a non-trivial task.
- **Implementer self-discipline on drift-guard integrity** (friction #4). Implementer B actually ran the test-then-break-then-fix check manually before freezing the test. This is behavior I briefed in the prompt; the agent followed through.
- **Commits per phase held.** Both branches have three commits each, each commit message cites the spec and phase. No squashing, no drift.

## Retrospective (fill in after you've slept on it)

-
