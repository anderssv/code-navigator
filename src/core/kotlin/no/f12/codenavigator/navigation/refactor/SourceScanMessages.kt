package no.f12.codenavigator.navigation.refactor

import java.io.File

/**
 * What a refactoring says when it scanned nothing, or didn't find what it was asked to move. "No changes
 * needed" is only true when the thing was found and already in place — reporting it for a scan of zero files
 * reads as success while nothing was even looked at.
 */
object SourceScanMessages {

    fun noSources(sourceRoots: List<File>): String {
        val roots = if (sourceRoots.isEmpty()) {
            "  (no source roots were given)"
        } else {
            sourceRoots.joinToString("\n") { root -> "  ${root.path}${if (root.exists()) "" else "  (does not exist)"}" }
        }
        return "No Kotlin (.kt) source files found, so nothing was scanned and nothing could be changed. Looked under:\n$roots\n" +
            "Check that the project's Kotlin source directory (usually src/main/kotlin) is one of the source roots."
    }

    fun classNotFound(className: String, scannedFiles: Int, sourceRoots: List<File>): String =
        "Class $className was not found in the $scannedFiles Kotlin source file(s) scanned under: ${sourceRoots.joinToString { it.path }}. " +
            "Check the fully qualified name, and that the class is declared in a .kt file (Java sources are not searched)."

    fun fileNotFound(fromFile: String, scannedFiles: Int, sourceRoots: List<File>): String =
        "Source file $fromFile was not found among the $scannedFiles Kotlin source file(s) scanned under: ${sourceRoots.joinToString { it.path }}. " +
            "The path is matched against the end of each file's path (e.g. com/example/Foo.kt)."
}
