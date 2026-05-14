package no.f12.codenavigator.navigation.callgraph

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.SourceSet

data class CollapsedUsage(
    val callerClass: ClassName,
    val callerMethod: String,
    val sourceFile: String,
    val targetOwner: ClassName,
    val kinds: Set<String>,
    val sourceSet: SourceSet?,
)

object UsageCollapser {

    private val INSTANTIATION_TARGETS = setOf("new", "<init>", "checkcast")

    /** Coroutine continuation methods that should collapse to the enclosing source method. */
    private val COROUTINE_METHODS = setOf("invoke", "invokeSuspend", "create")

    fun collapse(usages: List<UsageSite>): List<CollapsedUsage> {
        data class GroupKey(
            val callerClass: ClassName,
            val callerMethod: String,
            val targetOwner: ClassName,
        )

        return usages
            .groupBy { GroupKey(it.callerClass.collapseLambda(), collapseCallerMethod(it), it.targetOwner.collapseLambda()) }
            .map { (key, sites) ->
                val kinds = mutableSetOf<String>()
                for (site in sites) {
                    if (site.targetName in INSTANTIATION_TARGETS) {
                        kinds.add("instantiation")
                    } else {
                        kinds.add(classifyKind(site))
                    }
                }
                CollapsedUsage(
                    callerClass = key.callerClass,
                    callerMethod = key.callerMethod,
                    sourceFile = sites.first().sourceFile,
                    targetOwner = key.targetOwner,
                    kinds = kinds.toSortedSet(),
                    sourceSet = sites.first().sourceSet,
                )
            }
            // Filter self-referential: caller top-level class == target top-level class
            .filter { it.callerClass.topLevelClass() != it.targetOwner.topLevelClass() }
            .sortedWith(compareBy({ it.callerClass }, { it.callerMethod }))
    }

    private fun classifyKind(site: UsageSite): String = when {
        site.targetName in INSTANTIATION_TARGETS -> "instantiation"
        site.kind == UsageKind.FIELD_ACCESS -> "field-access"
        site.kind == UsageKind.METHOD_CALL -> "method-call"
        site.kind == UsageKind.TYPE_REFERENCE -> "type-reference"
        else -> site.kind.name.lowercase()
    }

    private fun collapseCallerMethod(site: UsageSite): String {
        val method = site.callerMethod
        if (method == "<field>") return "<field>"
        if (method.contains("\$lambda\$")) return method.substringBefore("\$lambda\$")
        // Collapse coroutine continuation methods — the real source method is encoded
        // in the inner class name segments, but we can't reliably extract it.
        // Collapse to a generic marker so they group together.
        if (method in COROUTINE_METHODS && site.callerClass.isSynthetic()) return "<coroutine>"
        return method
    }
}
