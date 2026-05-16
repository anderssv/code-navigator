package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.navigation.relations.implementors.ImplementorInfo

/**
 * Result of a "smart" find-usages query that auto-detects whether the target
 * is an interface and includes its implementations alongside the usage list.
 */
data class SmartUsageResult(
    /** Non-empty when the target type is an interface in the project. */
    val implementations: List<ImplementorInfo>,
    /** Usages of the target type (and optionally its implementors). */
    val usages: List<UsageSite>,
    /** All distinct target types that matched the query pattern. */
    val matchedTypes: List<ClassName> = emptyList(),
    /** Subset of [matchedTypes] that are interfaces. */
    val interfaceTypes: Set<ClassName> = emptySet(),
)
