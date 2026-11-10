# Plans for substantial changes

Use a task-specific plan for multi-module features, risky refactors, or work
that will span sessions. Small fixes and documentation edits do not need one.
Store plans as `docs/plans/<descriptive-name>.md`; create the directory when needed.
Existing subsystem plans remain useful background, not instructions to start work.

Include:

1. Goal and observable acceptance criteria.
2. Relevant current code, constraints, and compatibility requirements.
3. Ordered implementation steps with concrete files or targets.
4. Focused build/test commands and any manual checks.
5. Progress, significant decisions, blockers, and remaining work.

Keep the plan self-contained and update it as discoveries change the approach.
Distinguish planned, implemented, and validated work. Record unavailable checks
honestly. Do not duplicate the full plan into project memory or instruction files.
