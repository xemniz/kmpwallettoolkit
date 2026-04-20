# runs/ — experiment log

One file per run. Fill in as you go — not after. The PDF is explicit about this; the failure modes are the article and they evaporate from memory within hours.

## Filename convention
`<phase>-<task-ids>-<yyyymmdd>.md` — e.g. `baseline-A-20260420.md`, `parallel-AB-20260421.md`.

## Per-run file uses `run-template.md` verbatim.
Copy it. Don't try to normalize later.

## Rules

- **Tokens:** snapshot at end of run — use the session's usage summary. One number is fine.
- **Interventions:** every single time you typed into the agent to course-correct, clarify, or unblock. If you are deciding whether it "counts", it counts. This is the key signal.
- **Rework rate:** after the agent says it's done, count lines-changed-by-you-in-review / lines-changed-by-agent. Approximate with `git diff --stat` at the commit boundary. One digit of precision.
- **Failure modes:** copy the agent's exact output. Do not paraphrase. Paraphrasing is where the article dies.
- **Don't edit prior runs.** If you realize something later, append a "retrospective" section to the run file. History is part of the data.

## Phases to capture (per the PDF)
1. `baseline-A-*.md` — single session, one task, no subagents. The control.
2. `parallel-AB-*.md` — two concurrent worktrees, A and B. The experiment.
3. `parallel-ABC-*.md` or similar — scaled up, only if parallel-AB succeeded. Skip if it didn't — failure is content.
