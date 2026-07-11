package no.f12.codenavigator.navigation.refactor

import java.io.File
import java.nio.file.Path

/**
 * A source file's on-disk path and text, read directly rather than through a compiler frontend.
 * The move rewriters operate on file *text* (package/import lines, FQN references), so they don't
 * need a parsed AST for the file as a whole — only the targeted PSI reference rewrite ([retargetTypeReferences])
 * parses on demand, per file, from this text.
 *
 * [printAll] and [path] mirror the shapes the rewriters used when this wrapped OpenRewrite's `SourceFile`,
 * so the surrounding textual logic didn't have to change when OpenRewrite was removed.
 */
data class SourceFileContent(val path: String, val content: String) {
    fun printAll(): String = content
}

data class ParsedSources(
    val sources: List<SourceFileContent>,
    val sourceRoots: List<File>,
)

/**
 * Reads every `.kt` file under [sourceRoots] into memory. [classpath] is accepted for signature
 * compatibility with the previous OpenRewrite-backed parser (callers still thread a classpath
 * through) but is unused — the textual/PSI rewrites don't need type attribution.
 */
fun parseKotlinSources(
    sourceRoots: List<File>,
    @Suppress("UNUSED_PARAMETER") classpath: List<Path> = emptyList(),
): ParsedSources {
    val sources = collectSourceFiles(sourceRoots).map { SourceFileContent(it.absolutePath, it.readText()) }
    return ParsedSources(sources, sourceRoots)
}

fun collectSourceFiles(sourceRoots: List<File>): List<File> =
    sourceRoots.flatMap { root ->
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

/** The absolute path a source file was read from. Kept as a function (rather than a field access) so the many call sites that read `resolveOriginalPath(sf, roots)` compiled unchanged through the OpenRewrite removal. */
fun resolveOriginalPath(sourceFile: SourceFileContent, @Suppress("UNUSED_PARAMETER") sourceRoots: List<File>): String =
    sourceFile.path

/**
 * Checks if [fqn] matches [targetClassName] or its companion object.
 * Companion objects appear as "Outer.Companion" (dot-separated) in Kotlin source and
 * "Outer$Companion" (dollar sign) in bytecode. We check both forms.
 */
fun matchesClassOrCompanion(fqn: String?, targetClassName: String): Boolean {
    if (fqn == null) return false
    if (fqn == targetClassName) return true
    if (fqn == "$targetClassName.Companion") return true
    if (fqn == "${targetClassName}\$Companion") return true
    return false
}
