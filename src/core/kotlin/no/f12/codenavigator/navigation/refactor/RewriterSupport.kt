package no.f12.codenavigator.navigation.refactor

import org.openrewrite.ExecutionContext
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.SourceFile
import org.openrewrite.kotlin.KotlinParser
import java.io.File
import java.nio.file.Path

data class ParsedSources(
    val sources: List<SourceFile>,
    val sourceRoots: List<File>,
    val ctx: ExecutionContext,
)

fun parseKotlinSources(
    sourceRoots: List<File>,
    classpath: List<Path> = emptyList(),
): ParsedSources {
    val sourceFiles = collectSourceFiles(sourceRoots)
    val parserBuilder = KotlinParser.builder()
    if (classpath.isNotEmpty()) {
        parserBuilder.classpath(classpath)
    }
    val parser = parserBuilder.build()
    val ctx = InMemoryExecutionContext { it.printStackTrace() }
    val parsed = parser.parse(
        sourceFiles.map { it.toPath() },
        null,
        ctx,
    ).toList()
    return ParsedSources(parsed, sourceRoots, ctx)
}

fun collectSourceFiles(sourceRoots: List<File>): List<File> =
    sourceRoots.flatMap { root ->
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

fun resolveOriginalPath(sourceFile: SourceFile, sourceRoots: List<File>): String {
    val relativePath = sourceFile.sourcePath.toString()
    for (root in sourceRoots) {
        val candidate = File(root, relativePath)
        if (candidate.exists()) return candidate.absolutePath
    }
    return relativePath
}

/**
 * Checks if [fqn] matches [targetClassName] or its companion object.
 * OpenRewrite represents companion objects as "Outer.Companion" (dot-separated).
 * Bytecode uses "Outer$Companion" (dollar sign). We check both forms.
 */
fun matchesClassOrCompanion(fqn: String?, targetClassName: String): Boolean {
    if (fqn == null) return false
    if (fqn == targetClassName) return true
    if (fqn == "$targetClassName.Companion") return true
    if (fqn == "${targetClassName}\$Companion") return true
    return false
}
