package no.f12.codenavigator.navigation.refactor

import org.openrewrite.SourceFile
import java.io.File

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
