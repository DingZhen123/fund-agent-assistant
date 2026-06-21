# Fund Agent Assistant Project Instructions

## Project Direction

The goal of this project is to build a stable, secure, verifiable, traceable, recoverable, and continuously evolvable intelligent assistant for fund-related workflows.

The current project priority is to build an Agent Harness reliability system, including:

- verifiable task execution;
- traceable behavior and results;
- classified and recoverable failures;
- controlled high-risk operations;
- measurable Agent versions that can be evaluated, released gradually, and rolled back.

When priorities conflict, use this order:

```text
fund safety
> correctness
> verifiability
> recoverability
> maintainability
> feature richness
> development speed
```

## Required Reading

- Read `docs/agent-harness-goals.md` before work that affects Agent architecture, reliability, verification, recovery, safety, evaluation, release, or project direction.
- Read `docs/dag-agent-design.md` before changing DAG planning, binding, scheduling, execution, node completion, verification, or replanning.
- Read `docs/dag-agent-roadmap.md` when a task affects implementation order or an existing DAG milestone.
- Read and follow `docs/ai-development-workflow.md` before implementing a new feature, architectural change, cross-module refactor, public contract change, or behavior-changing failure-handling logic.

## Architectural Boundaries

- The DAG Runtime is the primary execution architecture for complex tasks.
- Simple tasks may use a shorter execution path, but they must follow the same safety, traceability, verification, and evaluation requirements.
- A capability represents a controlled business ability. A tool represents one concrete way to execute that ability.
- MCP is a pluggable tool integration mechanism. Internal tools are not required to use MCP.
- Do not increase architectural complexity merely to introduce a new Agent pattern or framework.
- Prefer the smallest end-to-end implementation that can be tested and verified.
- Treat DAG, ReAct, workflows, and sub-agents as implementation strategies, not as measures of system maturity.

## Fund and Information Safety

- Clearly distinguish read-only operations from operations with side effects.
- Fund transfers, sending, mutation, deletion, and other high-risk actions require deterministic permission checks and user confirmation when appropriate.
- Never retry a non-idempotent action blindly.
- When an action result is uncertain, inspect the real environment state before attempting it again.
- Do not expose secrets or unnecessary sensitive information in prompts, logs, traces, errors, tests, or documentation.
- Permissions, budgets, confirmation requirements, and safety constraints must be enforced in code, not only through prompts.
- If an operation cannot be shown to be safe, stop and report the risk.

## Authorization to Modify

- Modify files only when the user explicitly asks to implement, modify, fix, develop, refactor, create, or delete something.
- Requests to explain, analyze, discuss, design, review, diagnose, or suggest do not authorize file changes.
- Without modification authorization, perform only read-only inspection and provide findings or proposals.
- A direct request to fix a simple bug or make a simple change is sufficient authorization for that scoped change.
- New features and the high-impact changes listed below require design discussion and explicit approval of the implementation direction before production code is changed.
- Do not commit, push, create branches, create pull requests, modify databases, call production services, or perform external side effects unless the user explicitly requests the specific action.
- Preserve existing user changes and avoid unrelated edits.

## Changes Requiring Design Approval

Discuss the goal, scope, non-goals, design, risks, acceptance criteria, and test plan before implementing:

- new features;
- architectural changes;
- public API, schema, or protocol changes;
- database schema changes or data migrations;
- authentication, authorization, or fund-safety changes;
- retry, idempotency, side-effect, recovery, or confirmation behavior;
- failure-handling changes that alter observable behavior;
- cross-module refactors;
- production or deployment behavior.

If implementation reveals a material scope increase or a new safety risk, stop and request direction before continuing.

## TDD and Testing

Behavior changes must follow test-driven development where practical:

1. Add a test that expresses the expected behavior and fails before the implementation.
2. Add the smallest implementation that makes the test pass.
3. Refactor only while protected by passing tests.
4. Run relevant regression tests.

Bug fixes should begin with a regression test that reproduces the defect.

Pure documentation, comments, formatting, and configuration changes that cannot reasonably use an automated failing test may use another explicit verification method.

Do not weaken, delete, or bypass a test merely to make a change pass without explaining and obtaining approval for the changed behavior.

## Definition of Done

A feature or behavior change is complete only when:

- it has explicit, observable acceptance criteria;
- the acceptance criteria are satisfied;
- relevant automated tests pass;
- important failure paths and boundary cases are covered;
- fund safety, permissions, sensitive data, idempotency, and side effects have been considered;
- relevant regression tests pass;
- behavior-changing documentation is updated;
- unverified assumptions, skipped tests, and known limitations are reported.

Compilation alone does not prove that a feature is complete.

## Delivery Report

When handing off an implementation, report:

- what changed;
- which files changed;
- how each acceptance criterion was satisfied;
- which tests and verification commands were run and their results;
- which tests were not run;
- remaining risks, assumptions, or limitations.

