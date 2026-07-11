package no.f12.codenavigator.navigation.refactor

import java.io.File

data class RenameMethodResult(
    val changes: List<RenameChange>,
) {
    fun toJson(): String = """{"changes":${changesToJson(changes)}}"""

    companion object {
        fun fromJson(json: String): RenameMethodResult {
            val obj = parseJsonObject(json)
            return RenameMethodResult(changesFromJson(obj))
        }
    }
}

/** A human-readable explanation for a rename that produced no source edits, plus follow-up hints. */
data class NoChangeDiagnosis(val message: String, val hints: List<String>)

object RenameMethodRewriter {

    /**
     * Explain an empty rename result. "No changes needed" is ambiguous — it hides three very
     * different situations. Using bytecode we can tell them apart:
     *  - the class isn't in the bytecode at all (wrong/partial FQN, or not built),
     *  - the method IS declared but has no `.kt` declaration to edit (generated, or a `.java` source),
     *  - the method simply doesn't exist (offer a did-you-mean over the class's real methods).
     */
    fun diagnoseNoChanges(
        classesRoots: List<File>,
        className: String,
        methodName: String,
    ): NoChangeDiagnosis {
        val simple = className.substringAfterLast('.')
        if (classesRoots.isEmpty()) {
            return NoChangeDiagnosis(
                "No changes needed. (Compiled classes were unavailable, so '$methodName' on '$className' could not be verified — build first for a precise diagnosis.)",
                listOf(
                    "Ensure the class name is fully qualified (e.g., com.example.MyClass).",
                    "Only Kotlin source files (.kt) are searched for declarations.",
                ),
            )
        }

        val presence = RenameLocationFinder.inspectMethod(classesRoots, className, methodName)
        return when {
            !presence.classFound -> NoChangeDiagnosis(
                "Class '$className' was not found in compiled bytecode — nothing was renamed.",
                listOf(
                    "Ensure the class name is fully qualified and the project has been built.",
                    "Use cnavClassDetail --pattern=$simple to find the correct FQN.",
                ),
            )
            presence.methodDeclared -> NoChangeDiagnosis(
                "Method '$methodName' exists on '$className' but has no Kotlin declaration to rename — " +
                    "it is likely generated (e.g. JAXB/protobuf/data-class accessor) or declared in a non-Kotlin (.java) source. Skipped.",
                listOf(
                    "Rename it at its origin: the generator template/schema, or the .java file. cnavRenameMethod only edits .kt declarations.",
                ),
            )
            else -> {
                val suggestion = didYouMean(methodName, presence.declaredMethodNames)
                val declared = presence.declaredMethodNames.sorted().joinToString(", ").ifEmpty { "(none)" }
                NoChangeDiagnosis(
                    "No method '$methodName' found on '$className'." + (suggestion?.let { " Did you mean '$it'?" } ?: ""),
                    listOf(
                        "Declared methods on '$simple': $declared",
                        "Only Kotlin source files (.kt) are searched.",
                    ),
                )
            }
        }
    }

    /** Closest declared name within a small edit distance, or null. Keeps suggestions conservative. */
    private fun didYouMean(target: String, candidates: Set<String>): String? {
        val threshold = maxOf(2, target.length / 3)
        return candidates
            .map { it to levenshtein(target, it) }
            .filter { it.second <= threshold }
            .minByOrNull { it.second }
            ?.first
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }

    fun rename(
        sourceRoots: List<File>,
        className: String,
        methodName: String,
        newName: String,
        preview: Boolean = false,
        classesRoots: List<File> = emptyList(),
    ): RenameMethodResult {
        // Phase B: Use bytecode to find call sites and the full declaration set when classes are available.
        val callSiteFiles: Set<String>
        val implementorFqns: Set<String>
        if (classesRoots.isNotEmpty()) {
            callSiteFiles = RenameLocationFinder.findCallSiteFiles(classesRoots, className, methodName)
            // The whole override family must be renamed together: direct implementors of the target PLUS,
            // when the target is itself an override (e.g. an Impl), the interface it overrides and that
            // interface's sibling implementors — otherwise the impl ends up overriding nothing.
            implementorFqns = RenameLocationFinder.findImplementors(classesRoots, className) +
                RenameLocationFinder.findOverrideFamily(classesRoots, className, methodName)
        } else {
            callSiteFiles = emptySet()
            implementorFqns = emptySet()
        }

        return RenameMethodEditor.rename(
            sourceRoots = sourceRoots,
            className = className,
            methodName = methodName,
            newName = newName,
            preview = preview,
            callSiteFiles = callSiteFiles,
            implementorFqns = implementorFqns,
        )
    }
}
