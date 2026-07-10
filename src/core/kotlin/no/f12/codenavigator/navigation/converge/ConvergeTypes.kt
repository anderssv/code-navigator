package no.f12.codenavigator.navigation.converge

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

enum class ConvergeMode {
    INTERSECT,
    RISK,
    ;

    companion object {
        fun parse(value: String?): ConvergeMode = when (value) {
            "risk" -> RISK
            else -> INTERSECT
        }
    }
}

enum class ConvergeVerdict {
    /** Structural problem (cycle or ring violation) AND high change coupling — both independent signals agree. */
    ACT_NOW,

    /** Structural problem with no supporting coupling signal yet — a latent risk, not urgent. */
    LATENT,

    /** No structural problem detected, but the packages change together — coupling with no explicit contract. */
    MISSING_ABSTRACTION,
}

data class ConvergedEdge(
    val source: PackageName,
    val target: PackageName,
    val verdict: ConvergeVerdict,
    val hasCycle: Boolean,
    val hasRingViolation: Boolean,
    /** Coupling degree (0-100+ %) between the two packages, or null if no coupling signal exists for this pair. */
    val couplingDegree: Int?,
)

data class ConvergeIntersectOutput(
    val edges: List<ConvergedEdge>,
    /** Coupled file pairs that couldn't be resolved to a project package (e.g. non-source files) — reported for transparency, not shown per-pair. */
    val unresolvedCouplingPairs: Int,
    val skippedFileWarning: String?,
)

data class ConvergeRiskEntry(
    val className: ClassName,
    val sourceFile: String,
    val changeFrequency: Int,
    val complexity: Int,
    /** Max coupling degree involving this file's source, or null if it has no coupling signal (treated as neutral in the score, not zero). */
    val couplingDegree: Int?,
    val riskScore: Long,
)

data class ConvergeRiskOutput(
    val entries: List<ConvergeRiskEntry>,
    val skippedFileWarning: String?,
)

sealed class ConvergeOutput {
    data class Intersect(val output: ConvergeIntersectOutput) : ConvergeOutput()
    data class Risk(val output: ConvergeRiskOutput) : ConvergeOutput()
}
