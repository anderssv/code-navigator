package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.navigation.types.ClassName

/** How the set of domain classes being checked was arrived at — reported so the result is auditable. */
enum class SubjectSource {
    RINGS,
    DOMAIN_PATTERN,
}

/**
 * Whether the project already threads a clock through its code. [ABSENT] means no class anywhere
 * holds or receives a `Clock`, so reading the wall clock isn't drift from an established practice —
 * it's the only practice the project has. Findings are then advisory rather than violations.
 */
enum class ClockConvention {
    INJECTED,
    ABSENT,
}

data class AmbientAnalysisConfig(
    val domainClasses: Set<ClassName>,
    val categories: Set<AmbientCategory>,
    val exclude: Regex? = null,
    val subjectSource: SubjectSource = SubjectSource.RINGS,
)

data class AmbientResult(
    val violations: List<AmbientCall>,
    val initializerViolations: List<AmbientCall>,
    val domainClassCount: Int,
    val clockInjectingClasses: Set<ClassName>,
    val subjectSource: SubjectSource,
) {
    val all: List<AmbientCall> get() = violations + initializerViolations

    val clockConvention: ClockConvention
        get() = if (clockInjectingClasses.isEmpty()) ClockConvention.ABSENT else ClockConvention.INJECTED

    /**
     * A CLOCK finding in a project that has never injected a clock is advisory: it describes a design
     * choice, not a departure from one. Every other category stands on its own — randomness and I/O
     * in a domain class are untestable regardless of what the rest of the project does.
     */
    val actionable: List<AmbientCall>
        get() = all.filter { it.category != AmbientCategory.CLOCK || clockConvention == ClockConvention.INJECTED }

    fun byClass(): Map<ClassName, List<AmbientCall>> =
        all.groupBy { it.callerClass.topLevelClass() }
            .toSortedMap(compareBy { it.value })

    fun countsByCategory(): Map<AmbientCategory, Int> =
        all.groupingBy { it.category }.eachCount()
}

object AmbientBuilder {

    fun analyze(scan: AmbientScan, config: AmbientAnalysisConfig): AmbientResult {
        val relevant = scan.calls.filter { call ->
            call.category in config.categories &&
                call.callerClass.topLevelClass() in config.domainClasses &&
                config.exclude?.containsMatchIn(call.callerClass.value) != true
        }
        val (initializers, bodies) = relevant.partition { it.site == AmbientSite.INITIALIZER }

        return AmbientResult(
            violations = bodies.sortedWith(compareBy({ it.callerClass.value }, { it.line ?: 0 })),
            initializerViolations = initializers.sortedWith(compareBy({ it.callerClass.value }, { it.line ?: 0 })),
            domainClassCount = config.domainClasses.size,
            clockInjectingClasses = scan.clockInjectingClasses,
            subjectSource = config.subjectSource,
        )
    }
}
