# MAYA Mission Control — Expanded Feature Roadmap

## Goal
Evolve the existing Android app into one integrated Hybrid AI Factory. This roadmap is additive: preserve current screens, working workflows, local data, voice features, coding tools, mission queue, wallet ledger, research and visual automation. Do not create a separate app or replace existing implementations.

## Product rules
- A dashboard tile is navigation only; it must never imply a service is live.
- Every integration exposes one of: Not configured, Needs permission, Ready, Running, Succeeded, Failed, or Paused.
- Never report success until the provider or local executor returns a verified result.
- Require explicit user approval for external side effects (publishing, sending, purchases, money movement, deleting files, applying code patches, or operating another app).
- Keep secrets out of source code, logs, analytics, backups and exported reports. Use Android Keystore-backed encryption for locally stored credentials.
- Prefer local-first persistence for drafts, missions and history; make cloud sync opt-in.
- All long-running work must be cancellable, resumable where safe, bounded, and observable.
- Add migrations and tests before changing persisted schemas. Do not delete or silently reset user data.

## Feature inventory and implementation status

### Existing foundations to reuse
- Mission Control navigation catalogue and visual dashboard.
- Mission Commander, local Mission Queue, four-agent Planner/Researcher/Developer/Reviewer workflow and saved reports.
- Agent roster/workforce screens.
- Android App Factory / IDE coding assistant, SAF project access, reviewed patch workflow, Termux build handoff and offline project audit.
- Website Factory screen (verify actual generation/persistence capabilities before calling it production-ready).
- Maya Vision image understanding.
- User-confirmed Visual Automation through Android AccessibilityService.
- Tavily research client (requires a valid user-provided key).
- Voice/app launch, settings, local wallet ledger, history and diagnostics foundations.

### New or incomplete capabilities to implement in stages
1. **Maya Commander / Central AI Brain** — intent classification, task decomposition, capability registry, agent routing, approval gates and final evidence-based report.
2. **Smart Agent Collaboration** — typed task/handoff contracts, shared mission context, retries, timeouts, cancellation, reviewer gates and per-agent status. Begin with the existing four-agent pipeline; expand specialist roles only when they have real executors.
3. **Visual Workflow Builder** — safe node graph (trigger, AI task, condition, approval, delay/schedule, local action, notification), validation, versioning, persistence and run history.
4. **Browser & Computer Automation** — start with supported browser/provider integration and user-visible confirmation; do not promise arbitrary cross-app control. Keep accessibility actions opt-in and previewed.
5. **Maya Vision Intelligence** — image/document import, OCR where supported, structured extraction, citations/source references, privacy controls and explicit upload consent.
6. **AI Software Factory 2.0** — project templates, bounded repository scan, patch proposal, diff review, tests/build, artifact collection and rollback/backup. Never apply a generated patch without user confirmation.
7. **Security & Permission Center** — Android runtime permissions, accessibility status, provider connections, credential health, scoped access, audit log and revoke/disconnect controls.
8. **AI Performance & Cost Monitor** — provider/model usage, latency, success/failure, estimated token/cost where returned by provider, local task metrics and retention controls. Mark estimates as estimates.
9. **Long-Term Mission Memory** — user-approved project facts, mission summaries, retrieval with provenance, edit/export/delete controls and no secret storage.
10. **Mission Scheduler & Notifications** — WorkManager/JobScheduler-backed scheduling, constraints, retries, cancellation, reboot behavior and truthful deferred/blocked states.
11. **Multi-AI Router** — provider capability registry, model selection/fallback, key validation, privacy policy, budget limits and per-task provider transparency.
12. **Self-Healing / Recovery** — classify transient vs permanent failures, bounded retry with backoff, checkpoints, idempotency keys and manual recovery; never loop indefinitely.
13. **Knowledge Vault** — indexed user-selected documents and code, local metadata, citations back to source, storage limits and deletion controls.
14. **Backup & Restore** — encrypted export/import, schema versioning, integrity checks, preview before restore and no automatic overwrite.

## Suggested delivery phases

### Phase 0 — Baseline and safety (first)
- Verify current branch/PR head and CI.
- Map every dashboard shortcut to a real page ID and an implemented screen.
- Audit existing data models, repositories, navigation and permissions.
- Add feature-status contracts and an integration capability registry.
- Add regression tests for existing mission queue, agent pipeline, wallet ledger, voice routing and project patch review.
- Keep draft PR open; do not merge.

### Phase 1 — Intelligence foundation
- Central Commander orchestration over existing capabilities.
- Typed agent handoffs and mission lifecycle.
- Shared mission context and evidence-based final report.
- Provider/model router foundation and explicit configuration states.

### Phase 2 — Workflow and reliable execution
- Persisted visual workflow definitions and run history.
- Scheduler, cancellation, bounded retry and recovery.
- Notifications and approval queue.

### Phase 3 — Creation and knowledge
- Strengthen Software Factory around existing IDE bridge.
- Knowledge Vault and document-aware research.
- Vision extraction and source-linked results.
- Website Factory generation, preview, validation and export only after backend capability is verified.

### Phase 4 — Automation and integrations
- Browser automation through a supported, authenticated provider.
- Expand computer actions only with preview, permission and confirmation.
- Add optional media providers and external integrations through user-owned credentials.

### Phase 5 — Security, observability and release
- Keystore-backed secret storage and security center.
- Usage/cost telemetry with privacy controls.
- Encrypted backup/restore.
- Full CI, meaningful unit tests, instrumentation tests where feasible, APK artifact upload and real-device validation.

## Additional future feature ideas (planning backlog)
- **Mission Templates & Marketplace:** reusable user-created workflows, import/export with permission review.
- **Maya Skills Studio:** define a skill with inputs, tools, limits, tests and version history.
- **Human Approval Inbox:** one place to approve/reject sensitive actions and inspect exact consequences.
- **Project Context Profiles:** separate memory, keys and policies per project.
- **Offline Readiness Center:** show which tasks work offline, cached dependencies/models, storage and device constraints.
- **Disaster Recovery / Undo Center:** restore checkpoints and reverse only actions that are actually reversible.
- **Multi-device Handoff:** securely continue a mission on another enrolled device after explicit pairing.
- **Accessibility & Language Studio:** Bengali-first UX, multilingual voice settings, captions and accessible controls.
- **Quality Gate Dashboard:** required checks, test coverage, lint, build artifacts and known limitations before release.
- **Data Governance Center:** retention, export, deletion, upload consent and provider data-handling summaries.

## Acceptance criteria for every feature
- A visible screen state tied to actual repository/provider state.
- Loading, empty, success, partial-success, failure and permission/configuration states.
- Input validation, cancellation and bounded resource use.
- Unit tests for business logic and persistence migrations; integration/UI tests for critical paths where practical.
- No secrets in logs or source.
- Documentation identifies external prerequisites and unimplemented boundaries.
- CI result linked to the exact commit; device testing reported separately from CI.

## Current release truth
The existing PR must remain a draft until the full transfer scope is implemented and validation is complete. A successful compile is not equivalent to device verification or proof that external APIs are configured. The current GitHub Actions workflow should be extended to publish a debug APK artifact after a successful build, then verify artifact availability.
