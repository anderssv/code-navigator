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

object RenameMethodRewriter {

    fun rename(
        sourceRoots: List<File>,
        className: String,
        methodName: String,
        newName: String,
        preview: Boolean = false,
        classesRoots: List<File> = emptyList(),
    ): RenameMethodResult {
        // Phase B: Use bytecode to find call sites and implementors when classes are available
        val callSiteFiles: Set<String>
        val implementorFqns: Set<String>
        if (classesRoots.isNotEmpty()) {
            callSiteFiles = RenameLocationFinder.findCallSiteFiles(classesRoots, className, methodName)
            implementorFqns = RenameLocationFinder.findImplementors(classesRoots, className)
        } else {
            callSiteFiles = emptySet()
            implementorFqns = emptySet()
        }

        return PsiRenameMethodRewriter.rename(
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
