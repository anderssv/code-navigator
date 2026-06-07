package no.f12.codenavigator.navigation.refactor

import java.io.File
import java.nio.file.Path

object MoveFileRewriter {

    fun move(
        sourceRoots: List<File>,
        fromFile: String,
        toPackage: String,
        classpath: List<Path> = emptyList(),
        preview: Boolean = false,
        parsedSources: ParsedSources? = null,
    ): MoveClassResult {
        val ps = parsedSources ?: run {
            val sourceFiles = collectSourceFiles(sourceRoots)
            if (sourceFiles.isEmpty()) return MoveClassResult(emptyList())
            parseKotlinSources(sourceRoots, classpath)
        }
        if (ps.sources.isEmpty()) return MoveClassResult(emptyList())

        // Find the source file matching the given path
        val normalizedFrom = fromFile.replace("\\", "/")
        val resolvedFile = ps.sources.firstOrNull { sourceFile ->
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            val normalizedFilePath = filePath.replace("\\", "/")
            normalizedFilePath == normalizedFrom ||
                normalizedFilePath.endsWith("/$normalizedFrom") ||
                normalizedFilePath.endsWith(normalizedFrom) ||
                sourceFile.sourcePath.toString().let { sp ->
                    val normalizedSp = sp.replace("\\", "/")
                    normalizedSp == normalizedFrom || normalizedSp.endsWith("/$normalizedFrom") || normalizedSp.endsWith(normalizedFrom)
                }
        } ?: return MoveClassResult(emptyList())

        val filePath = resolveOriginalPath(resolvedFile, ps.sourceRoots)
        val content = resolvedFile.printAll()

        // Detect current package
        val packageMatch = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(content)
            ?: return MoveClassResult(emptyList())
        val oldPackage = packageMatch.groupValues[1]

        if (oldPackage == toPackage) return MoveClassResult(emptyList())

        // Detect all declared classes
        val declaredClasses = MoveClassRewriter.extractDeclaredClassNames(content)

        if (declaredClasses.isEmpty()) {
            // Pure top-level file — use Kt facade path
            val fileName = File(filePath).nameWithoutExtension
            val ktFacadeName = "${fileName}Kt"
            val oldFqcn = "$oldPackage.$ktFacadeName"
            val newFqcn = "$toPackage.$ktFacadeName"
            return MoveClassRewriter.move(sourceRoots, oldFqcn, newFqcn, classpath, preview, ps, allowMultiClass = true)
        }

        // Use first declared class as the entry point — the multi-class logic handles the rest
        val primaryClass = declaredClasses.first()
        val oldFqcn = "$oldPackage.$primaryClass"
        val newFqcn = "$toPackage.$primaryClass"
        return MoveClassRewriter.move(sourceRoots, oldFqcn, newFqcn, classpath, preview, ps, allowMultiClass = true)
    }
}
