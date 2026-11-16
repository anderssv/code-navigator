package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.registry.TaskRegistry
import java.io.File

/**
 * `pattern=Person.actionsOn`, the form find-callers and find-usages take, accepted by the refactoring tasks too, as an
 * alternative to `target-class` + `method`. The part after the last dot is the method unless it starts with an
 * upper-case letter, in which case the whole pattern names a class (safe-delete of a class).
 */
object TargetPattern {

    data class Target(val className: String, val methodName: String?)

    fun parse(properties: Map<String, String?>): Target? {
        val pattern = properties[TaskRegistry.TARGET_PATTERN.name]?.takeIf { it.isNotBlank() } ?: return null
        val last = pattern.substringAfterLast(".")
        return if (!pattern.contains('.') || last.first().isUpperCase()) Target(pattern, null)
        else Target(pattern.substringBeforeLast("."), last)
    }

    fun className(properties: Map<String, String?>): String =
        parse(properties)?.className ?: properties[TaskRegistry.RENAME_CLASS.name]
            ?: throw IllegalArgumentException("Missing '${TaskRegistry.TARGET_PATTERN.name}' (Class.method) or '${TaskRegistry.RENAME_CLASS.name}'")

    fun methodName(properties: Map<String, String?>): String? =
        parse(properties)?.methodName ?: properties[TaskRegistry.RENAME_METHOD.name]

    fun requiredMethodName(properties: Map<String, String?>): String =
        methodName(properties)
            ?: throw IllegalArgumentException("Missing method: pass '${TaskRegistry.TARGET_PATTERN.name}=Class.method' or '${TaskRegistry.RENAME_METHOD.name}'")
}

/** Turns a simple class name (`Person`) into the fully qualified one from the sources; anything with a dot is kept. */
object TargetClassResolver {

    fun resolve(sourceRoots: List<File>, name: String): String {
        if (name.contains('.')) return name
        val candidates = sourceRoots.asSequence()
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") } }
            .mapNotNull { file ->
                val content = file.readText()
                if (!MoveClassRewriter.extractTopLevelClassNames(content).contains(name)) return@mapNotNull null
                MoveClassRewriter.packageOf(content)?.let { "$it.$name" } ?: name
            }
            .distinct().toList()
        return when (candidates.size) {
            0 -> name
            1 -> candidates.single()
            else -> throw IllegalArgumentException("'$name' matches several classes, use the fully qualified name: ${candidates.sorted().joinToString(", ")}")
        }
    }
}
