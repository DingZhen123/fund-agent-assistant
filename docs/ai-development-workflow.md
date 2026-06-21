# AI-Assisted Development Workflow

## 1. Purpose

This document defines how the user and a coding Agent collaborate on software changes in this repository.

The workflow is intended to prevent:

- coding before the problem is understood;
- moving directly from a concept discussion to an architectural implementation;
- uncontrolled scope growth;
- unauthorized file changes;
- treating compilation as proof of completion;
- features without tests or acceptance criteria;
- unsafe changes to fund-related or side-effecting behavior.

The workflow follows these principles:

```text
clear goal
→ explicit boundaries
→ explicit authorization
→ small implementation
→ test-driven development
→ evidence-based verification
```

The mandatory lifecycle for standard and high-risk changes is:

```text
Explore → Define → Design → Approve → Implement → Verify → Report
```

## 2. Authorization Boundary

Only an explicit request to implement, modify, fix, develop, refactor, create, or delete something authorizes file changes.

The following requests do not authorize changes:

- explain;
- analyze;
- discuss;
- design;
- review;
- diagnose;
- investigate;
- recommend.

Without authorization, the Agent may:

- read source code and documentation;
- run relevant read-only inspections;
- explain the current behavior;
- identify risks and alternatives;
- propose acceptance criteria and a design;
- provide examples without writing them to the repository.

Without authorization, the Agent must not modify files, databases, configuration, remote services, or other external state.

## 3. Task Classification

### 3.1 Simple Change

A change is simple only when all of the following are true:

- the desired behavior is unambiguous;
- the change is local and low risk;
- it does not alter a public interface or persistent data;
- it does not affect fund safety, permissions, sensitive data, or side effects;
- it does not change retry, recovery, idempotency, or failure semantics;
- it requires no architectural choice;
- it is easy to test and roll back.

Examples include:

- correcting documentation or text;
- fixing a clearly scoped local defect;
- adding a missing regression test;
- making a small null-safety correction with established expected behavior.

After explicit modification authorization, a simple change may proceed directly to TDD implementation and verification.

### 3.2 Standard Change

A change is standard when it has any of these characteristics:

- it adds business behavior;
- it changes multiple files or modules;
- it adds a model, interface, or integration;
- it changes an existing execution path;
- it requires new acceptance criteria.

A standard change must follow:

```text
Define → Design → Approve → Implement → Verify → Report
```

### 3.3 High-Risk Change

A change is high risk when it involves:

- fund movement or fund-related decisions;
- authentication or authorization;
- secrets or sensitive information;
- external side effects;
- non-idempotent operations;
- automatic retries or recovery;
- database schema changes or data migration;
- public protocol or compatibility changes;
- Agent Runtime, DAG, verification, or safety policy behavior;
- production systems or deployment behavior.

A high-risk change requires:

- a written design;
- explicit safety analysis;
- acceptance criteria;
- a test plan;
- failure and rollback strategies;
- explicit user approval before implementation.

## 4. Explore

### Goal

Build shared understanding without changing code.

### Agent Responsibilities

- Inspect relevant code and documentation.
- Describe the current implementation and behavior.
- Explain how the concept relates to this project.
- Identify assumptions, unknowns, risks, and constraints.
- Compare reasonable approaches.
- Recommend a direction and explain the trade-offs.

### Restrictions

- Do not modify files.
- Do not silently expand the task.
- Do not treat interest in an approach as implementation approval.

### Expected Output

- current understanding;
- key findings;
- available approaches;
- recommendation;
- unresolved decisions.

## 5. Define

Before design or implementation, define:

```text
Background:
Why is this change needed?

Goal:
What observable result must this change produce?

Scope:
Which modules and behaviors may change?

Non-goals:
What is explicitly excluded?

Acceptance criteria:
How will completion be proven?

Risks:
Does the change affect funds, permissions, sensitive data,
side effects, compatibility, or production behavior?
```

The Agent should restate these items and identify material ambiguity.

The Agent may proceed with an explicit, low-risk assumption when it does not affect safety or the overall design. The assumption must be reported. Otherwise, the Agent must request a decision.

### Acceptance-Criteria Quality

Acceptance criteria must:

- describe observable behavior;
- be testable or inspectable;
- cover the successful path;
- cover important failure and boundary paths;
- avoid subjective language without a measurement method.

Weak criterion:

```text
Make the Agent more stable.
```

Strong criterion:

```text
Retry a query tool at most twice when it returns
SYSTEM_ERROR with retryable=true.

Never automatically retry the non-idempotent receipt-send tool.

Record a TraceEvent for every retry decision.
```

## 6. Design

Standard and high-risk changes require a design before implementation.

The design should include, as applicable:

- current behavior and call flow;
- proposed behavior and call flow;
- data models and interface changes;
- modules and files likely to change;
- compatibility impact;
- success, failure, and boundary paths;
- fund and information-safety impact;
- permission, confirmation, idempotency, and side-effect behavior;
- observability and trace requirements;
- test strategy;
- migration, rollback, or degradation strategy;
- alternatives and trade-offs.

The design should seek the smallest end-to-end solution that satisfies the acceptance criteria.

The design phase does not authorize production-code changes. After presenting the design, the Agent must obtain explicit approval before implementation.

## 7. Approve

Implementation may begin only when:

- the user has explicitly requested implementation; and
- required design questions have been resolved; and
- the user has approved the implementation direction for a standard or high-risk change.

Approval is scoped to the agreed goal and boundaries. It does not authorize unrelated cleanup, redesign, refactoring, Git operations, database changes, or production actions.

If the agreed scope materially changes during implementation, return to Define or Design and obtain renewed approval.

## 8. Implement

After approval, the Agent must:

1. Inspect the working tree and preserve existing user changes.
2. Translate acceptance criteria into tests or explicit verification checks.
3. Add a failing test for the expected behavior where practical.
4. Implement the smallest change that makes the test pass.
5. Refactor only when protected by passing tests.
6. Avoid unrelated cleanup and speculative abstractions.
7. Keep the implementation readable, testable, and reversible.
8. Stop and report if the scope, risk, or architecture changes materially.

### TDD Cycle

```text
Red → Green → Refactor
```

#### Red

Create a test that expresses the desired behavior and fails for the expected reason before implementation.

#### Green

Write only enough production code to satisfy the test and acceptance criterion.

#### Refactor

Improve structure while preserving behavior and keeping tests green.

Bug fixes should begin with a regression test that reproduces the defect.

Exceptions to test-first development may include:

- documentation-only changes;
- comments;
- formatting with no behavior change;
- configuration that cannot reasonably be exercised automatically.

For an exception, define and report an alternative verification method.

## 9. Verify

Verification must be independent from the claim that implementation is finished.

At minimum, verify:

- each acceptance criterion;
- relevant automated tests;
- important failure paths;
- boundary conditions;
- permission and side-effect behavior;
- idempotency and retry behavior where applicable;
- sensitive-data handling;
- relevant regression behavior;
- documentation impact;
- assumptions that remain unverified.

The following alone do not prove completion:

- files were created;
- code was written;
- the project compiled;
- one narrow test passed;
- a model judged its own implementation correct.

For fund-related or side-effecting behavior, prefer deterministic checks and real-state inspection over model self-assessment.

## 10. Report

The implementation handoff must include:

```text
Summary:
What changed?

Files:
Which files changed?

Acceptance:
How was each acceptance criterion satisfied?

Verification:
Which tests and commands were run, and what were the results?

Not verified:
Which tests or environments were unavailable or intentionally skipped?

Risks and limitations:
What assumptions, limitations, or follow-up work remain?
```

Do not hide failed tests, skipped verification, incomplete behavior, or known risks.

Use precise completion language:

- `Complete` only when all required criteria and verification have passed.
- `Implementation complete; verification pending` when code exists but required checks have not run.
- `Partially complete` when only part of the agreed scope is implemented.
- `Blocked` when an external decision or unavailable dependency prevents progress.

## 11. Scope Changes and Stop Conditions

Stop implementation and report before continuing when:

- the requirement conflicts with the current architecture;
- a public contract must change unexpectedly;
- a database migration becomes necessary;
- a fund-safety or information-security risk is discovered;
- an unapproved module or external system must be changed;
- reliable testing cannot be established;
- existing user changes conflict with the implementation;
- acceptance criteria cannot be satisfied as agreed;
- a supposedly idempotent operation may produce duplicate side effects;
- production access or irreversible action becomes necessary.

The report should state:

- what was discovered;
- why it changes the agreed approach;
- available options;
- the recommended option;
- the decision required from the user.

## 12. Git and External Operations

Unless explicitly requested, the Agent must not:

- create or switch branches;
- stage or commit changes;
- push commits;
- create pull requests;
- delete user files;
- modify database data;
- invoke production services;
- send messages;
- initiate fund operations;
- install system-level dependencies.

Irreversible actions and actions affecting external people or systems require separate confirmation.

## 13. Definition of Done

A task is complete only when:

- implementation was explicitly authorized;
- the goal, scope, and acceptance criteria are clear;
- the implementation follows the agreed architecture;
- required tests and verification pass;
- important failure and boundary paths are covered;
- fund and information safety have been checked;
- relevant documentation is updated;
- skipped checks and known limitations are disclosed.

If any required condition is unmet, do not describe the task as fully complete.

