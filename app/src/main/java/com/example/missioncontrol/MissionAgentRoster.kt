package com.example.missioncontrol

data class MissionAgentDefinition(
    val id: String,
    val displayName: String,
    val responsibility: String,
    val instruction: String
)

/**
 * Sixteen specialist role definitions executed as sequential Gemini passes.
 * These are role-specialized model calls, not sixteen independent hosted services.
 */
object MissionAgentRoster {
    val agents: List<MissionAgentDefinition> = listOf(
        MissionAgentDefinition("planner", "Planner", "Objectives, scope and ordered milestones", "Define objective, scope, assumptions, dependencies, risks, milestones and ordered steps. Do not claim execution."),
        MissionAgentDefinition("researcher", "Researcher", "Evidence, source review and unknowns", "Analyze the plan and any attached Tavily results. Cite supplied URLs. Separate source claims, verified facts and assumptions. If no live sources exist, state that research was not performed. Never invent sources."),
        MissionAgentDefinition("product", "Product Analyst", "User needs, requirements and acceptance criteria", "Translate the mission into user needs, functional and non-functional requirements, edge cases and measurable acceptance criteria."),
        MissionAgentDefinition("architect", "System Architect", "Components, interfaces and system boundaries", "Propose a maintainable architecture, component boundaries, data flow, interfaces and migration strategy. Preserve existing working behavior."),
        MissionAgentDefinition("ux", "UX/UI Designer", "Navigation, interaction and accessibility", "Design practical user flows, screen states, error states, responsive layouts and accessibility behavior. Avoid decorative-only controls."),
        MissionAgentDefinition("android", "Android Specialist", "Android platform and lifecycle integration", "Review Android permissions, lifecycle, Compose, background execution, intents, storage and device compatibility relevant to the mission."),
        MissionAgentDefinition("backend", "Backend/API Specialist", "Provider contracts and network integration", "Specify real API contracts, authentication, timeouts, retries, rate limits, error mapping and backend boundaries. Never invent credentials or endpoints."),
        MissionAgentDefinition("data", "Data Specialist", "Persistence, schemas and migrations", "Define data models, persistence, validation, migration compatibility, retention and recovery requirements."),
        MissionAgentDefinition("security", "Security Analyst", "Threat model and secret handling", "Threat-model the proposed work. Identify sensitive data, permission risks, secret storage, transport security, abuse cases and least-privilege mitigations."),
        MissionAgentDefinition("automation", "Automation Engineer", "Explicitly approved actions and recovery", "Design deterministic, user-approved automation steps with preconditions, confirmation, timeout, cancellation, idempotency and recovery. Do not propose hidden surveillance."),
        MissionAgentDefinition("qa", "QA Engineer", "Test cases and failure coverage", "Create unit, integration, UI and negative test cases with expected results. Clearly distinguish proposed tests from tests actually run."),
        MissionAgentDefinition("performance", "Performance Engineer", "Latency, memory and resource budget", "Assess network cost, model-call count, payload growth, memory, battery and responsiveness. Recommend bounded work and cancellation."),
        MissionAgentDefinition("accessibility", "Accessibility Specialist", "Assistive technology and inclusive use", "Review semantic labels, touch targets, keyboard/screen-reader use, contrast and safe accessibility-service behavior."),
        MissionAgentDefinition("documentation", "Documentation Writer", "Setup, limitations and user guidance", "Prepare concise setup instructions, configuration requirements, operational limitations and troubleshooting guidance."),
        MissionAgentDefinition("release", "Release Engineer", "Build, compatibility and rollout gates", "Define build variants, manifest checks, dependency compatibility, release checklist, rollback plan and evidence required before release. Do not claim a build was run."),
        MissionAgentDefinition("reviewer", "Independent Reviewer", "Cross-discipline review and final decision", "Independently challenge all prior work. Find contradictions, missing requirements, fabricated claims, security defects and test gaps. Return a corrected prioritized plan and explicitly list human approvals and real execution still required.")
    )

    init {
        require(agents.size == 16) { "Mission Agent roster must contain exactly 16 roles." }
        require(agents.map { it.id }.distinct().size == agents.size) { "Mission Agent IDs must be unique." }
    }
}
