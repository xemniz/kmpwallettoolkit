---
description: Run one or more specs under specs/ through the planner → implementer → crypto-reviewer pipeline. Multiple specs run in parallel (unless hot-file-contended, in which case they serialize).
argument-hint: <spec-id> [<spec-id>...] [--quick]
---

# /implement — orchestrate the full agent pipeline

You are the orchestrator for this repo's parallel-agent workflow. A user invoked `/implement` with one or more spec identifiers. Execute the playbook below. Do not ask clarifying questions unless a spec fails to resolve or a blocker is detected in §0.

## Arguments
- One or more spec identifiers. Accept either:
  - Short form: `A`, `G`, etc. — resolves to the single matching `specs/<letter>-*.md` (non-`.plan.md`) file via Glob.
  - Explicit path: `specs/A-eip1559.md`.
- Optional `--quick`: skip the planner stage. Only use for docs-only tweaks, one-line typo fixes, rename of a non-public symbol, formatting. **Never** for crypto-adjacent code, hot-file edits, new modules, or anything in wallet-core's expect/actual seam.

## Rules carried from the repo — do not violate
- Never run `./gradlew build`. Always per-module `:allTests`. See `CLAUDE.md` §3.
- Never modify `WalletTest.kt`, `EvmSigningPayloadTest.kt`, any existing test file a spec doesn't explicitly name — `CLAUDE.md` §4 rule 6 (no assertion weakening).
- Never alter a golden-vector JSON string — `CLAUDE.md` §4 rule 8.
- Empty-commit-per-phase is forbidden. Only artifact-producing phases get commits — `.claude/agents/implementer.md` updated rule.
- Reviewer must independently revert-and-reproduce any "pre-existing red" claim — `.claude/agents/crypto-reviewer.md` D.17.

## Playbook

### 0. Pre-flight
1. **Resolve specs.** For each arg, `Glob specs/<letter>-*.md` (excluding `.plan.md`). Must match exactly one. If zero or many, stop and ask.
2. **Check git state.** `git status --porcelain` must be empty. If not, stop and ask the user whether to stash/commit.
3. **Check branch.** Must be on `main`. If not, stop and ask.
4. **Read every spec.** Extract from each:
   - `## Module(s) touched` — the scope fence.
   - `## Files expected to change` — the file list.
   - Any `## Non-goals` — forbidden-edit context.
5. **Detect hot-file contention.** Scan the merged file list. Hot files per `CLAUDE.md` §7:
   - `gradle/libs.versions.toml`
   - `settings.gradle.kts`
   - `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/Chain.kt`
   - `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/ChainRegistry.kt`
   - If two or more specs touch the same hot file → **serialize** those specs (one after another, in the order given). Independent specs still run in parallel alongside the serialized chain.
6. **Announce the plan.** One short message to the user: which specs run parallel, which serialize, what the hot-file trigger was if any. Do not wait for approval — proceed unless the user interrupts.

### 1. Planner stage (skip if `--quick`)
- Spawn **one planner per spec, all in parallel in a single message.** Use `subagent_type: "planner"` if the harness lists it; otherwise `general-purpose` with the planner prompt inlined. Each planner reads:
  - `.claude/agents/planner.md` (its operating manual)
  - `CLAUDE.md`
  - Its spec
  - Only the module(s) the spec touches — do not read sample apps
- Each planner writes `specs/<id>.plan.md`.
- When all return: commit all plans in one commit (`Planner output for specs <ids>`).
- Fast-forward any existing worktree branches to include the plan commit.

### 2. Worktrees
- For each spec that will run now (all parallel specs + the first of any serialized chain):
  - `git worktree add -b task/<letter>-<slug> ../<repo>-<letter> main`
- Slug = the first noun in the spec's filename (e.g. `eip1559`, `bnb`, `bip39`).
- The `isolation: worktree` parameter on the Agent tool is gated on the env snapshot's "is git repo" flag. If the parameter refuses, use manual worktrees (as above) and spawn without that parameter — tell each implementer its absolute worktree path in the prompt.

### 3. Implementer stage
- Spawn **one implementer per active spec, parallel in a single message.** Each prompt includes:
  - Absolute worktree path (**every Bash call must `cd <path> && ...`**).
  - Spec + plan paths (read from the worktree, not main).
  - Scope fence: the "Files expected to change" list as allowed, with explicit forbidden list for hot files and adjacent modules.
  - **Pre-authorization for hot files** if the plan calls for one — include the line "Hot-file edit authorized by orchestrator; document in commit body."
  - Test gate commands (from `CLAUDE.md` §3, module-scoped).
  - New commit rule: artifact-producing phases only, no `--allow-empty`.
- Background mode (`run_in_background: true`). Wait for completion notifications.
- On return, note the commit SHAs and any issues flagged.

### 4. Serialized-chain merge-ahead (only if hot-file contention)
- After the first implementer in a serialized chain returns, go through its reviewer and merge first (steps 5 + 6) before starting the next.
- Fast-forward the next worktree onto post-merge main.
- Spawn its implementer with a prompt note: "Spec <prior> has merged; you see its artifacts in your base."

### 5. Reviewer stage
- Spawn **one reviewer per branch, parallel where possible.** Each prompt includes:
  - Worktree path, branch, base (`main`).
  - D.17 mandate: for any "pre-existing red" claim, independently revert-and-reproduce.
  - Byte-identical diff check on any test file the spec names as "must not change."
  - Scope compliance check (every changed path under the allowed modules).
  - Run the module's `:allTests` gate — do not trust the implementer's green.
- Background mode. Wait.

### 6. Merge (only on MERGE verdicts)
- Any HOLD or REJECT: **stop**, summarize the reviewer's must-fix items to the user, await direction. Do not attempt to "fix and retry" without explicit instruction.
- All MERGE: for each branch, `git merge --no-ff task/<…> -m "Merge task/<…> — <summary>"` with the reviewer's verdict cited in the body.
- Remove worktrees. Keep branches.

### 7. Run log
- Create `runs/<pattern>-<yyyymmdd>.md` from `runs/run-template.md`.
- `<pattern>` = `<letters>` joined (e.g. `A`, `AB`, `CEF`, `GH`).
- Fill in:
  - Token counts per agent from the task-notification `usage` blocks.
  - Interventions (orchestrator steps you took — pre-flight fixes, hot-file authorizations, fast-forwards).
  - Rework rate: `0 / <total-inserted-lines>` pre-user-review, with a note that this is pre-review.
  - Failure modes observed — especially absences of PDF-predicted modes (platform leak, golden drift, test weakening, hot-file race, KSP corruption, Gradle OOM).
  - Success moments worth citing.
- Commit the run log.

### 8. Report
- One summary message: branches merged, verdict per task, rework rate, any should-fixes surfaced (flag as follow-up candidates), run log path.
- If any should-fix could become a future `/implement` task, name it clearly so the user can ask you to spin one up.

## Notes on "complex enough"
- **Parallelism is a function of task count, not single-task complexity.** Two independent simple specs run in parallel. One genuinely complex spec runs single-pipeline (it still gets planner → implementer → reviewer; the "complex" part is per-agent depth, not cross-agent fan-out).
- If the user ever asks "is this task complex enough for parallel?" the answer is "parallel is for multiple tasks; one task stays on one pipeline no matter how big."
- If the user wants an interpretation call (e.g. "split this spec into two for parallelism"), raise it explicitly — don't silently re-scope.

## `--quick` scope (narrow on purpose)
Allowed without the planner:
- Typo fixes in source or docs.
- Rename of a non-public internal symbol.
- Comment/KDoc tweaks.
- `.gitignore` / gradle.properties non-daemon tweaks (still requires user approval per CLAUDE.md §8).

Forbidden with `--quick`:
- Any change under `wallet-core/src/commonMain`, `wallet-core/src/iosMain`, or any expect/actual declaration.
- Any change to a serialization-relevant file (`Eip1559Transaction.kt`, `EvmSigningPayload.kt`, `JsonRpcModels.kt`, etc.).
- Any new test file that asserts a golden value.
- Any hot file.
- Any new module.

When in doubt, drop `--quick` and run the full pipeline. The planner stage is ~40k tokens; that's cheap compared to a silent crypto regression.
