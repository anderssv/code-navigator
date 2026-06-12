package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet

/**
 * Counts how many edges (violations, cycle edges, coupled pairs) involve a test-source class, so
 * architecture tasks can surface a factual line when run without a scope filter. Rather than guess
 * a "test edges dominate" threshold, we print the raw ratio and let the reader decide.
 */
object TestInvolvement {

    data class Counts(val testInvolved: Int, val total: Int)

    /**
     * @param edges class-to-class edges underlying the result (source, target).
     * @param sourceSetOf resolves a class to its source set (null = unknown, treated as non-test).
     */
    fun count(edges: List<Pair<ClassName, ClassName>>, sourceSetOf: (ClassName) -> SourceSet?): Counts {
        val testInvolved = edges.count { (source, target) ->
            sourceSetOf(source) == SourceSet.TEST || sourceSetOf(target) == SourceSet.TEST
        }
        return Counts(testInvolved = testInvolved, total = edges.size)
    }

    /**
     * One-line factual notice for scope=all runs. [noun] is the result unit, e.g. "violations",
     * "cycle edges", "coupled pairs". Returns null when there is nothing to report (no edges).
     */
    fun notice(counts: Counts, noun: String): String? {
        if (counts.total == 0) return null
        return "test-involvement: ${counts.testInvolved} of ${counts.total} $noun involve test sources. " +
            "Re-run with --scope=prod for production-only architecture signal."
    }
}
