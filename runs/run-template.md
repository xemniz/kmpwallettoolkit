# Run: <phase> — <task ids> — <yyyy-mm-dd>

**Phase:** <baseline | parallel-AB | parallel-ABC | ...>
**Task(s):** <A, B, ...>
**Agent config:** <single session | planner+implementer | planner+implementer+crypto-reviewer | teams>
**Worktrees:** <none | A=<path>, B=<path>, ...>
**Concurrency:** <sequential | N tmux panes | Agent Teams>

## Timing

| Marker                      | Time (local 24h) | Notes |
|-----------------------------|------------------|-------|
| Start of planning           |                  |       |
| Plan approved               |                  |       |
| First commit by implementer |                  |       |
| Implementer declared done   |                  |       |
| Reviewer declared done      |                  |       |
| PR-ready (green on CI gate) |                  |       |

**Total wall clock (start → PR-ready):** <hh:mm>

## Token usage

| Agent              | Tokens  | Notes |
|--------------------|---------|-------|
| Main session       |         |       |
| planner            |         |       |
| implementer        |         |       |
| crypto-reviewer    |         |       |
| **Total**          |         |       |

## Interventions
Count every time you typed into the agent to course-correct, clarify, or unblock. Include the short reason. Ambiguous = counts.

| # | Agent | Turn | Why I stepped in | Category |
|---|-------|------|------------------|----------|
| 1 |       |      |                  |          |

Categories: `spec-ambiguity`, `conventions-miss`, `crypto-rule-violation`, `test-weakened`, `platform-leak`, `wrong-module`, `dependency-drift`, `daemon-issue`, `tool-failure`, `other`.

## Rework rate

Measured at the implementer-declared-done boundary, before the reviewer's human edits (if any).

- Lines changed by agent (per `git diff main..<branch> --stat`): **<N>**
- Lines subsequently changed by me (per `git diff <agent-done-sha>..HEAD --stat`): **<M>**
- **Rework rate:** M / N = **<%>**

Notes (what did you rewrite and why):
-

## Failure modes observed

Copy agent output verbatim. One sub-section per distinct failure.

### Failure 1: <short label, e.g. "Used java.security in commonMain">
**When:** <phase / turn>
**Agent:**
```
<paste output>
```
**What I did:**
<paste correction>
**Root cause:**
<one sentence>

### Failure 2: ...

## Success moments worth citing in the article
Short, specific. Things the agent got right that a generic guide wouldn't have predicted.

-

## Retrospective (fill in only after you've slept on it)
-
