package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.formatting.TaskGuidance

object TestCouplingGuidance {

    val GUIDANCE = TaskGuidance(
        purpose = """
            Detects callers that violate Testing Through the Domain (TTTD) by calling port interface methods directly.
            In hexagonal architecture, ports are the interfaces at the boundary (Repository, Client, Gateway, Adapter).
            --subject=tests (default): tests should mutate system state through domain/service methods, not by
            calling port methods directly. This keeps tests resilient to internal changes and forces the domain
            API to be expressive.
            --subject=adapters: a driving adapter (a route/controller — not a service-tier class) calling a port
            WRITE method directly means the use-case orchestration that write implies has nowhere to live but the
            adapter. cnavRings cannot see this on its own — an adapter calling a port is exactly what dependency-
            direction analysis permits; only the *absence* of a service-tier class in between reveals it. Read-side
            pass-through from an adapter (repository.get(id) straight from a handler) is idiomatic and not flagged.
        """.trimIndent(),
        parameterGuidance = """
            Set --ports to a regex matching your port interface names (the boundaries that get faked in tests).
            Common patterns: ".*Repository|.*Client|.*Gateway|.*Adapter"
            To identify ports in your project: look for interfaces with both a production implementation
            and a fake/test implementation. These are the hexagonal architecture boundaries.
            Example: ApplicationRepository (prod: ApplicationRepositoryImpl, test: ApplicationRepositoryFake)
            --subject=adapters/both additionally classifies driving adapters via cnavRings' own adapter/service-
            tier detection — run cnavRings first if the split looks wrong, since fixing that fixes both checks.
            --write-methods/--read-methods (Iface.method, comma-separated) override the built-in write-verb
            heuristic (add/update/delete/save/create/insert/remove/finalize/migrate/persist/publish/send/
            register/revoke/archive/schedule/cancel/set) for --subject=adapters, e.g. a method that doesn't
            start with one of those verbs but does mutate.
        """.trimIndent(),
        interpretation = """
            [subject=tests] Flagged calls are test methods that directly call methods declared on port interfaces.
            To fix: replace direct port/adapter calls with the equivalent domain service operation.
            BEFORE: repository.addApplication(application) — data-oriented, coupled to storage
            AFTER: applicationService.register(application) — domain-oriented, resilient to change
            Read-only calls (get/find/fetch) for assertions are acceptable and shown separately.

            [subject=adapters] Flagged calls are a driving adapter calling a port write method directly, with no
            service-tier class in between. To fix: extract the orchestration into a domain service method the
            adapter calls instead, exactly as above but one layer over — the adapter becomes a thin translator
            (parse request -> call service -> render response), and the service absorbs the validate/mutate/
            persist/notify sequence that was living in the handler. A known limit: this only catches orchestration
            that touches a port; purely in-memory sequencing with no write anywhere in it will not be flagged.
        """.trimIndent(),
    )
}
