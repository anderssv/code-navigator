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
        moveTests: Boolean = false,
    ): MoveClassResult {
        val ps = parsedSources ?: run {
            val sourceFiles = collectSourceFiles(sourceRoots)
            if (sourceFiles.isEmpty()) return MoveClassResult(emptyList(), error = SourceScanMessages.noSources(sourceRoots))
            parseKotlinSources(sourceRoots, classpath)
        }
        if (ps.sources.isEmpty()) return MoveClassResult(emptyList(), error = SourceScanMessages.noSources(sourceRoots))

        // Find the source file matching the given path
        val normalizedFrom = fromFile.replace("\\", "/")
        val resolvedFile = ps.sources.firstOrNull { sourceFile ->
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            val normalizedFilePath = filePath.replace("\\", "/")
            normalizedFilePath == normalizedFrom ||
                normalizedFilePath.endsWith("/$normalizedFrom") ||
                normalizedFilePath.endsWith(normalizedFrom)
        } ?: return MoveClassResult(emptyList(), error = SourceScanMessages.fileNotFound(fromFile, ps.sources.size, sourceRoots))

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
            return MoveClassRewriter.move(sourceRoots, oldFqcn, newFqcn, classpath, preview, ps, allowMultiClass = true, movedFile = filePath, moveTests = moveTests).verifiedMovedFile(filePath)
        }

        // Use first declared class as the entry point — the multi-class logic handles the rest
        val primaryClass = declaredClasses.first()
        val oldFqcn = "$oldPackage.$primaryClass"
        val newFqcn = "$toPackage.$primaryClass"
        return MoveClassRewriter.move(sourceRoots, oldFqcn, newFqcn, classpath, preview, ps, allowMultiClass = true, movedFile = filePath, moveTests = moveTests).verifiedMovedFile(filePath)
    }

    /** The move must have moved the file that was asked for; anything else is a bug, so it fails instead of reporting success. */
    private fun MoveClassResult.verifiedMovedFile(requested: String): MoveClassResult {
        val moved = movedFilePath ?: return this
        if (error != null || moved == requested) return this
        return MoveClassResult(emptyList(), error = "Refusing to move: the move would have changed $moved instead of the requested file $requested. Nothing was written.")
    }
}
