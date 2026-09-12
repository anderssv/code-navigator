package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

enum class DirectiveKind {
    ADAPTER,
    NOT_ADAPTER,
    COMPOSITION_ROOT,
    SERVICE_TIER,
    NOT_SERVICE_TIER,
}

data class UnhonouredDirective(
    val kind: DirectiveKind,
    val pattern: String,
)

data class RingOverrideResult(
    val graph: RingGraph,
    val unhonoured: List<UnhonouredDirective> = emptyList(),
)

data class ServiceTierOverrideResult(
    val serviceTier: Set<ClassName>,
    val unhonoured: List<UnhonouredDirective> = emptyList(),
)

object RingConfigOverrides {

    fun apply(graph: RingGraph, config: RingsConfig): RingOverrideResult {
        val forcedAdapters = graph.classes.matching(config.adapters)
        val excludedAdapters = graph.classes.matching(config.notAdapters)
        val forcedRoots = graph.classes.matching(config.compositionRoots)

        return RingOverrideResult(
            graph = graph.copy(
                ioClasses = graph.ioClasses + forcedAdapters - excludedAdapters,
                adapterReasons = graph.adapterReasons +
                    forcedAdapters.associateWith { AdapterReason.CONFIGURED } -
                    excludedAdapters,
                compositionRoots = graph.compositionRoots + forcedRoots,
            ),
            unhonoured = unmatched(graph, config),
        )
    }

    /**
     * Applied separately from [apply] because service tier is derived (via [ServiceTierDetector])
     * from the graph *after* adapter overrides have already settled — there's no single-pass way to
     * override both at once.
     */
    fun applyServiceTier(serviceTier: Set<ClassName>, classes: Set<ClassName>, config: RingsConfig): ServiceTierOverrideResult {
        val forced = classes.matching(config.serviceTier)
        val excluded = classes.matching(config.notServiceTier)

        val unhonoured = listOf(
            DirectiveKind.SERVICE_TIER to config.serviceTier,
            DirectiveKind.NOT_SERVICE_TIER to config.notServiceTier,
        ).flatMap { (kind, patterns) ->
            patterns
                .filterNot { pattern -> classes.any { RingsConfig.matchesGlob(it.value, pattern) } }
                .map { UnhonouredDirective(kind, it) }
        }

        return ServiceTierOverrideResult(
            serviceTier = serviceTier + forced - excluded,
            unhonoured = unhonoured,
        )
    }

    private fun unmatched(graph: RingGraph, config: RingsConfig): List<UnhonouredDirective> =
        listOf(
            DirectiveKind.ADAPTER to config.adapters,
            DirectiveKind.NOT_ADAPTER to config.notAdapters,
            DirectiveKind.COMPOSITION_ROOT to config.compositionRoots,
        ).flatMap { (kind, patterns) ->
            patterns
                .filterNot { pattern -> graph.classes.any { RingsConfig.matchesGlob(it.value, pattern) } }
                .map { UnhonouredDirective(kind, it) }
        }

    private fun Set<ClassName>.matching(patterns: List<String>): Set<ClassName> =
        filter { cls -> patterns.any { RingsConfig.matchesGlob(cls.value, it) } }.toSet()
}
