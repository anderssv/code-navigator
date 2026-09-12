package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.formatting.TaskGuidance

object AmbientGuidance {

    val GUIDANCE = TaskGuidance(
        purpose = """
            Detects domain (ring-0) classes reading ambient, nondeterministic input directly from the
            platform: the wall clock (Instant.now(), System.currentTimeMillis()), randomness
            (UUID.randomUUID(), Math.random()), the environment (System.getenv()), or the filesystem
            and network. No dependency-direction check can see this — it is a static call on a type
            the file already imports for unrelated reasons (field declarations), not a constructor-
            injected dependency that would ever show up as a boundary crossing in cnavRings. The class
            looks pure from the outside and is untestable without freezing global state.
        """.trimIndent(),
        parameterGuidance = """
            By default the checked set is ring 0 as cnavRings computes it — run cnavRings first if the
            layering looks wrong, since fixing that fixes this check too. If cnavRings cannot produce a
            layered hexagon for the project, pass --domain=<regex> to name the domain classes directly
            (e.g. --domain="\.domain\.|\.model\.").
            --categories=clock,random,env,io narrows the check (default: all four).
            --detail lists every call site rather than one line per class.
            --exclude=<regex> drops classes by name.
        """.trimIndent(),
        interpretation = """
            Each finding is a call site with file:line. The fix is to make the value an input:
            BEFORE: fun isExpired() = deadline.isBefore(Instant.now())
            AFTER:  fun isExpired(now: Instant) = deadline.isBefore(now)   — or inject a Clock at the service edge.
            A zone overload (ZonedDateTime.now(APP_ZONE)) is still a finding: a zone changes which wall
            clock is read, not whether one is read. Passing a Clock (LocalDate.now(clock)) is not flagged.
            Findings in default arguments and initializers are listed separately — they are the most
            common hit and usually the cheapest fix (take the value as a parameter at the call site).
            Read the "Clock convention" line before acting on CLOCK findings: if no class in the project
            injects a Clock, those findings are advisory — they describe a design choice the project has
            made everywhere, not drift from its own practice. RANDOM, ENV and IO findings stand on their
            own regardless, since they make the domain untestable no matter what the rest of the project does.
        """.trimIndent(),
    )
}
