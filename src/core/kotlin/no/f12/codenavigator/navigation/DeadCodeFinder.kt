package no.f12.codenavigator.navigation

enum class DeadCodeKind {
    CLASS,
    METHOD,
}

data class DeadCode(
    val className: ClassName,
    val memberName: String?,
    val kind: DeadCodeKind,
    val sourceFile: String,
)

object DeadCodeFinder {

    fun find(
        graph: CallGraph,
        filter: Regex?,
        exclude: Regex?,
        classesOnly: Boolean,
    ): List<DeadCode> {
        val projectClasses = graph.projectClasses()
        if (projectClasses.isEmpty()) return emptyList()

        val calledTypes = mutableSetOf<ClassName>()
        val calledMethods = mutableSetOf<MethodRef>()
        val projectMethods = mutableSetOf<MethodRef>()

        graph.forEachEdge { caller, callee ->
            if (caller.className in projectClasses) {
                projectMethods.add(caller)
            }
            if (callee.className in projectClasses) {
                projectMethods.add(callee)
            }
            if (caller.className != callee.className && callee.className in projectClasses) {
                calledTypes.add(callee.className)
                calledMethods.add(callee)
            }
        }

        val results = mutableListOf<DeadCode>()

        for (cls in projectClasses) {
            if (cls !in calledTypes && !cls.isGenerated()) {
                results.add(
                    DeadCode(
                        className = cls,
                        memberName = null,
                        kind = DeadCodeKind.CLASS,
                        sourceFile = graph.sourceFileOf(cls),
                    )
                )
            }
        }

        if (!classesOnly) {
            for (method in projectMethods) {
                if (method.className in calledTypes &&
                    method !in calledMethods &&
                    !method.className.isGenerated() &&
                    !KotlinMethodFilter.isGenerated(method.methodName)
                ) {
                    results.add(
                        DeadCode(
                            className = method.className,
                            memberName = method.methodName,
                            kind = DeadCodeKind.METHOD,
                            sourceFile = graph.sourceFileOf(method.className),
                        )
                    )
                }
            }
        }

        return results
            .filter { item -> filter == null || item.className.matches(filter) }
            .filter { item -> exclude == null || !item.className.matches(exclude) }
            .sortedWith(compareBy({ it.kind }, { it.className }, { it.memberName ?: "" }))
    }
}
