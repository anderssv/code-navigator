package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.formatting.TaskGuidance

object TestCouplingGuidance {

    val GUIDANCE = TaskGuidance(
        purpose = """
            Detects tests that violate Testing Through the Domain (TTTD) by calling port interface methods directly.
            In hexagonal architecture, ports are the interfaces at the boundary (Repository, Client, Gateway, Adapter).
            Tests should mutate system state through domain/service methods, not by calling port methods directly.
            This keeps tests resilient to internal changes and forces the domain API to be expressive.
        """.trimIndent(),
        parameterGuidance = """
            Set --ports to a regex matching your port interface names (the boundaries that get faked in tests).
            Common patterns: ".*Repository|.*Client|.*Gateway|.*Adapter"
            To identify ports in your project: look for interfaces with both a production implementation
            and a fake/test implementation. These are the hexagonal architecture boundaries.
            Example: ApplicationRepository (prod: ApplicationRepositoryImpl, test: ApplicationRepositoryFake)
        """.trimIndent(),
        interpretation = """
            Flagged calls are test methods that directly call methods declared on port interfaces.
            To fix: replace direct port/adapter calls with the equivalent domain service operation.
            BEFORE: repository.addApplication(application) — data-oriented, coupled to storage
            AFTER: applicationService.register(application) — domain-oriented, resilient to change
            Read-only calls (get/find/fetch) for assertions are acceptable and shown separately.
        """.trimIndent(),
    )
}
