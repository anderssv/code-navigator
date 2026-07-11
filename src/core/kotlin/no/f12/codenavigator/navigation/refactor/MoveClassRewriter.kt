package no.f12.codenavigator.navigation.refactor

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** One requested class move for [MoveClassRewriter.moveBatch]. */
data class BatchMoveRequest(val className: String, val newFqcn: String)

data class MoveClassResult(
    val changes: List<RenameChange>,
    val movedFilePath: String? = null,
    val newFilePath: String? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
) {
    fun toJson(): String {
        val movedJson = movedFilePath?.let { ""","movedFilePath":"${jsonEscape(it)}"""" } ?: ""
        val newJson = newFilePath?.let { ""","newFilePath":"${jsonEscape(it)}"""" } ?: ""
        val warningsJson = if (warnings.isNotEmpty()) {
            ""","warnings":${warnings.joinToString(",", "[", "]") { "\"${jsonEscape(it)}\"" }}"""
        } else ""
        val errorJson = error?.let { ""","error":"${jsonEscape(it)}"""" } ?: ""
        return """{"changes":${changesToJson(changes)}$movedJson$newJson$warningsJson$errorJson}"""
    }

    companion object {
        fun fromJson(json: String): MoveClassResult = fromJsonObj(parseJsonObject(json))

        fun listToJson(results: List<MoveClassResult>): String =
            results.joinToString(",", "[", "]") { it.toJson() }

        fun listFromJson(json: String): List<MoveClassResult> =
            parseJsonObjectArray(json).map { fromJsonObj(it) }

        private fun fromJsonObj(obj: Map<String, Any?>): MoveClassResult {
            @Suppress("UNCHECKED_CAST")
            val warnings = (obj["warnings"] as? List<String>) ?: emptyList()
            return MoveClassResult(
                changes = changesFromJson(obj),
                movedFilePath = obj["movedFilePath"] as? String,
                newFilePath = obj["newFilePath"] as? String,
                warnings = warnings,
                error = obj["error"] as? String,
            )
        }
    }
}

object MoveClassRewriter {

    fun move(
        sourceRoots: List<File>,
        className: String,
        newFqcn: String,
        classpath: List<Path> = emptyList(),
        preview: Boolean = false,
        parsedSources: ParsedSources? = null,
        allowMultiClass: Boolean = false,
    ): MoveClassResult {
        val ps = parsedSources ?: run {
            val sourceFiles = collectSourceFiles(sourceRoots)
            if (sourceFiles.isEmpty()) return MoveClassResult(emptyList())
            parseKotlinSources(sourceRoots, classpath)
        }
        if (ps.sources.isEmpty()) return MoveClassResult(emptyList())

        val isKtFacade = isKtFacadeName(className)
        return if (isKtFacade) {
            moveKtFacade(className, newFqcn, ps, sourceRoots, preview)
        } else {
            moveClass(className, newFqcn, ps, sourceRoots, preview, allowMultiClass)
        }
    }

    /**
     * Moves many classes in one pass: parses [sourceRoots] once and runs the "simple" moves
     * (single class per file, standard filename) through one [CompositeRecipe] instead of one
     * `ChangeType` run per class. This is what makes `cnavMovePackage`/`cnavExecutePlan`
     * O(1) parses instead of O(N) — each parse loads the whole Kotlin compiler frontend, which
     * dominates the cost of a batch move (see plan.md "Test suite health" for the measurement
     * that ruled out just caching the parser object).
     *
     * `CompositeRecipe` only composes correctly when the constituent recipes touch *different*
     * files — two `ChangeType` recipes that both rewrite the same file's package declaration in
     * one composed run corrupt it (verified empirically: produces `package foo.<error>`). So
     * Kt facades and genuine multi-class files (two+ requested classes declared in the same file)
     * are routed through the existing single-class path instead, unchanged, using the shared
     * parse. Everything else — the common case — goes through the batched recipe.
     */
    fun moveBatch(
        sourceRoots: List<File>,
        moves: List<BatchMoveRequest>,
        classpath: List<Path> = emptyList(),
        preview: Boolean = false,
    ): List<MoveClassResult> {
        if (moves.isEmpty()) return emptyList()

        val sourceFiles = collectSourceFiles(sourceRoots)
        if (sourceFiles.isEmpty()) return moves.map { MoveClassResult(emptyList()) }
        val ps = parseKotlinSources(sourceRoots, classpath)
        if (ps.sources.isEmpty()) return moves.map { MoveClassResult(emptyList()) }

        val results = arrayOfNulls<MoveClassResult>(moves.size)
        val batchable = mutableListOf<BatchableMove>()

        for ((index, req) in moves.withIndex()) {
            if (isKtFacadeName(req.className)) {
                results[index] = moveKtFacade(req.className, req.newFqcn, ps, sourceRoots, preview)
                continue
            }

            val oldPackage = req.className.substringBeforeLast(".")
            val simpleClassName = req.className.substringAfterLast(".")
            val newPackage = req.newFqcn.substringBeforeLast(".")
            val targetName = req.newFqcn.substringAfterLast(".")

            var movedFilePath: String? = null
            var newFilePath: String? = null
            for (sourceFile in ps.sources) {
                val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
                if (isTargetClassFile(filePath, oldPackage, simpleClassName)) {
                    movedFilePath = filePath
                    newFilePath = computeNewFilePath(filePath, oldPackage, simpleClassName, newPackage, targetName, sourceRoots)
                    break
                }
            }
            if (movedFilePath == null) {
                for (sourceFile in ps.sources) {
                    val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
                    val content = sourceFile.printAll()
                    if (isInPackage(content, oldPackage) && declaresClass(content, simpleClassName)) {
                        movedFilePath = filePath
                        val fileName = File(filePath).nameWithoutExtension
                        newFilePath = computeNewFilePath(filePath, oldPackage, fileName, newPackage, fileName, sourceRoots)
                        break
                    }
                }
            }

            if (movedFilePath == null) {
                results[index] = MoveClassResult(emptyList())
                continue
            }

            val movedSource = ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == movedFilePath }?.printAll()
            val allClasses = movedSource?.let { extractDeclaredClassNames(it) } ?: emptyList()
            if (allClasses.size > 1) {
                // Genuine multi-class file: two ChangeType recipes in one CompositeRecipe run would
                // corrupt this file's package declaration. Fall back to the existing per-class path,
                // which handles it via independent recipe runs + a manual package-line replace.
                results[index] = moveClass(req.className, req.newFqcn, ps, sourceRoots, preview, allowMultiClass = false)
                continue
            }

            batchable.add(BatchableMove(index, req.className, req.newFqcn, oldPackage, simpleClassName, newPackage, movedFilePath, newFilePath))
        }

        if (batchable.isNotEmpty()) {
            runBatchedMoves(ps, batchable, preview, results)
        }

        return results.map { it ?: MoveClassResult(emptyList()) }
    }

    private data class BatchableMove(
        val index: Int,
        val className: String,
        val newFqcn: String,
        val oldPackage: String,
        val simpleClassName: String,
        val newPackage: String,
        val movedFilePath: String,
        val newFilePath: String?,
    )

    private fun runBatchedMoves(
        ps: ParsedSources,
        batchable: List<BatchableMove>,
        preview: Boolean,
        results: Array<MoveClassResult?>,
    ) {
        // The old CompositeRecipe applied every move's ChangeType in one pass, so a file touched by two
        // moves saw both. Replicate that by threading a mutable working copy of each source's text
        // through the moves in order — each move's retarget (and its package-line rewrite) layers on the
        // previous moves' edits.
        val originals = ps.sources.associate { it.path to it.content }
        val working = originals.toMutableMap()
        for (move in batchable) {
            val workingSources = working.map { (path, content) -> SourceFileContent(path, content) }
            for ((path, after) in KotlinTypeReferenceRewriter.retargetAcrossSources(workingSources, move.className, move.newFqcn)) {
                working[path] = after
            }
            working[move.movedFilePath]?.let { working[move.movedFilePath] = rewritePackageDecl(it, move.oldPackage, move.newPackage) }
        }

        val sharedChanges = mutableMapOf<String, RenameChange>()
        for ((path, finalContent) in working) {
            val original = originals[path] ?: continue
            if (finalContent != original) sharedChanges[path] = RenameChange(path, original, finalContent)
        }

        for (move in batchable) {
            val allSiblingNames = findSiblingClassNames(ps, move.oldPackage, move.simpleClassName)

            // A "sibling" might itself be moving elsewhere in this same batch. If it's moving to
            // the SAME package as this class, it stays same-package — no import needed at all. If
            // it's moving to a DIFFERENT package, point the import there instead of at oldPackage,
            // which will no longer contain it once its own move applies.
            val packageOverrides = mutableMapOf<String, String>()
            val staysImplicit = mutableSetOf<String>()
            for (sibling in allSiblingNames) {
                val coMoving = batchable.firstOrNull { it.oldPackage == move.oldPackage && it.simpleClassName == sibling }
                if (coMoving != null) {
                    if (coMoving.newPackage == move.newPackage) staysImplicit.add(sibling) else packageOverrides[sibling] = coMoving.newPackage
                }
            }
            val siblingNames = allSiblingNames - staysImplicit

            val movedContent = sharedChanges[move.movedFilePath]?.after
                ?: ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == move.movedFilePath }?.printAll()
            if (movedContent != null) {
                val updatedContent = addMissingImportsForSiblings(movedContent, move.oldPackage, siblingNames, packageOverrides)
                if (updatedContent != movedContent) {
                    val originalContent = sharedChanges[move.movedFilePath]?.before ?: movedContent
                    sharedChanges[move.movedFilePath] = RenameChange(move.movedFilePath, originalContent, updatedContent)
                }
            }

            val movedFileSource = ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == move.movedFilePath }?.printAll()
            if (movedFileSource != null) {
                val topLevelNames = extractTopLevelNames(movedFileSource)
                if (topLevelNames.isNotEmpty()) {
                    updateTopLevelImportsInConsumersMap(ps, sharedChanges, move.oldPackage, move.newPackage, move.movedFilePath, topLevelNames)
                }
            }
        }

        for (move in batchable) {
            val relevantPaths = mutableSetOf(move.movedFilePath)
            val oldImport = "import ${move.oldPackage}.${move.simpleClassName}"
            for ((filePath, change) in sharedChanges) {
                if (filePath == move.movedFilePath) continue
                if (change.before.contains(oldImport)) relevantPaths.add(filePath)
            }
            val changesForThisMove = sharedChanges.filterKeys { it in relevantPaths }.values.toList()
            val warnings = targetFileWarnings(move.newFilePath, move.movedFilePath)
            results[move.index] = MoveClassResult(changesForThisMove, move.movedFilePath, move.newFilePath, warnings)
        }

        if (!preview) {
            for (change in sharedChanges.values) {
                File(change.filePath).writeText(change.after)
            }
            for (move in batchable) {
                if (move.newFilePath != null && move.movedFilePath != move.newFilePath) {
                    val newFile = File(move.newFilePath)
                    newFile.parentFile.mkdirs()
                    Files.move(File(move.movedFilePath).toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun moveClass(
        className: String,
        newFqcn: String,
        ps: ParsedSources,
        sourceRoots: List<File>,
        preview: Boolean,
        allowMultiClass: Boolean = false,
    ): MoveClassResult {
        val oldPackage = className.substringBeforeLast(".")
        val simpleClassName = className.substringAfterLast(".")
        val newPackage = newFqcn.substringBeforeLast(".")
        val targetName = newFqcn.substringAfterLast(".")

        // Find the file containing the class
        var foundByContent = false
        var movedFilePath: String? = null
        var newFilePath: String? = null

        for (sourceFile in ps.sources) {
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            if (isTargetClassFile(filePath, oldPackage, simpleClassName)) {
                movedFilePath = filePath
                newFilePath = computeNewFilePath(filePath, oldPackage, simpleClassName, newPackage, targetName, sourceRoots)
                break
            }
        }

        // Fallback: search by content + package when filename doesn't match
        if (movedFilePath == null) {
            for (sourceFile in ps.sources) {
                val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
                val content = sourceFile.printAll()
                if (isInPackage(content, oldPackage) && declaresClass(content, simpleClassName)) {
                    movedFilePath = filePath
                    val fileName = File(filePath).nameWithoutExtension
                    newFilePath = computeNewFilePath(filePath, oldPackage, fileName, newPackage, fileName, sourceRoots)
                    foundByContent = true
                    break
                }
            }
        }

        // Early multi-class detection — stop and list unrequested siblings
        if (!allowMultiClass && movedFilePath != null) {
            val movedSource = ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == movedFilePath }?.printAll()
            if (movedSource != null) {
                val allClasses = extractDeclaredClassNames(movedSource)
                val siblings = allClasses.filter { it != simpleClassName }
                if (siblings.isNotEmpty()) {
                    val fileName = File(movedFilePath).name
                    return MoveClassResult(
                        changes = emptyList(),
                        error = buildString {
                            appendLine("Class $className is defined in $fileName, which also declares:")
                            for (sibling in siblings) {
                                appendLine("  - $sibling")
                            }
                            appendLine()
                            append("The entire file moves together. Use cnavMoveFile or cnavMoveClass --from-file to move the whole file.")
                        },
                    )
                }
            }
        }

        // If file contains multiple classes (allowMultiClass path), delegate to moveKtFacade-style logic
        if (movedFilePath != null && foundByContent) {
            val movedSource = ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == movedFilePath }?.printAll()
            if (movedSource != null) {
                val allClasses = extractDeclaredClassNames(movedSource)
                if (allClasses.size > 1) {
                    return moveMultiClassFile(oldPackage, newPackage, movedFilePath, movedSource, allClasses, ps, sourceRoots, preview, newFilePath)
                }
            }
        }

        val changes = retargetChanges(ps, className, newFqcn).toMutableList()

        // Check if standard-named file also has sibling classes
        if (movedFilePath != null && !foundByContent) {
            val movedSource = ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == movedFilePath }?.printAll()
            if (movedSource != null) {
                val allClasses = extractDeclaredClassNames(movedSource)
                if (allClasses.size > 1) {
                    return moveMultiClassFile(oldPackage, newPackage, movedFilePath, movedSource, allClasses, ps, sourceRoots, preview, newFilePath)
                }
            }
        }

        for (sourceFile in ps.sources) {
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            if (isTargetClassFile(filePath, oldPackage, simpleClassName)) {
                movedFilePath = filePath
                newFilePath = computeNewFilePath(filePath, oldPackage, simpleClassName, newPackage, targetName, sourceRoots)
                break
            }
        }

        // Fallback: if filename doesn't match class name, search by content + package
        if (movedFilePath == null) {
            for (sourceFile in ps.sources) {
                val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
                val content = sourceFile.printAll()
                if (isInPackage(content, oldPackage) && declaresClass(content, simpleClassName)) {
                    movedFilePath = filePath
                    val fileName = File(filePath).nameWithoutExtension
                    newFilePath = computeNewFilePath(filePath, oldPackage, fileName, newPackage, fileName, sourceRoots)
                    break
                }
            }
        }

        // If file contains sibling classes, also retarget references to them
        if (movedFilePath != null) {
            val movedSource = ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == movedFilePath }?.printAll()
            if (movedSource != null) {
                val siblingClasses = extractDeclaredClassNames(movedSource).filter { it != simpleClassName }
                for (sibling in siblingClasses) {
                    val oldSiblingFqcn = "$oldPackage.$sibling"
                    val newSiblingFqcn = "$newPackage.$sibling"
                    for (change in retargetChanges(ps, oldSiblingFqcn, newSiblingFqcn)) {
                        val existingIdx = changes.indexOfFirst { it.filePath == change.filePath }
                        if (existingIdx >= 0) {
                            changes[existingIdx] = RenameChange(change.filePath, changes[existingIdx].before, change.after)
                        } else {
                            changes.add(change)
                        }
                    }
                }
            }
        }

        // ChangeType used to rewrite the declaring file's own package line; retargetChanges does not,
        // so apply it to the moved file textually (same as the multi-class/facade paths' replacePackageImports).
        if (movedFilePath != null) {
            val movedIdx = changes.indexOfFirst { it.filePath == movedFilePath }
            val current = if (movedIdx >= 0) changes[movedIdx].after
            else ps.sources.firstOrNull { it.path == movedFilePath }?.content
            if (current != null) {
                val updated = rewritePackageDecl(current, oldPackage, newPackage)
                if (updated != current) {
                    val original = if (movedIdx >= 0) changes[movedIdx].before else current
                    if (movedIdx >= 0) changes[movedIdx] = RenameChange(movedFilePath, original, updated)
                    else changes.add(RenameChange(movedFilePath, original, updated))
                }
            }
        }

        importMovedTypeInFormerSamePackage(ps, changes, oldPackage, newPackage, targetName, movedFilePath)

        // Add imports for former same-package classes referenced by the moved file
        if (movedFilePath != null) {
            val siblingNames = findSiblingClassNames(ps, oldPackage, simpleClassName)
            val movedChangeIdx = changes.indexOfFirst { it.filePath == movedFilePath }
            val movedContent = if (movedChangeIdx >= 0) changes[movedChangeIdx].after else {
                ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == movedFilePath }?.printAll()
            }
            if (movedContent != null) {
                val updatedContent = addMissingImportsForSiblings(movedContent, oldPackage, siblingNames)
                if (updatedContent != movedContent) {
                    val originalContent = if (movedChangeIdx >= 0) changes[movedChangeIdx].before else movedContent
                    if (movedChangeIdx >= 0) {
                        changes[movedChangeIdx] = RenameChange(movedFilePath, originalContent, updatedContent)
                    } else {
                        changes.add(RenameChange(movedFilePath, originalContent, updatedContent))
                    }
                }
            }

            // Also update consumer imports for top-level declarations in the moved file
            val movedFileSource = ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == movedFilePath }?.printAll()
            if (movedFileSource != null) {
                val topLevelNames = extractTopLevelNames(movedFileSource)
                if (topLevelNames.isNotEmpty()) {
                    updateTopLevelImportsInConsumers(ps, changes, oldPackage, newPackage, movedFilePath, topLevelNames)
                }
            }
        }

        if (!preview) {
            applyChanges(changes, movedFilePath, newFilePath)
        }

        val warnings = targetFileWarnings(newFilePath, movedFilePath)
        return MoveClassResult(changes, movedFilePath, newFilePath, warnings)
    }

    private fun moveKtFacade(
        className: String,
        newFqcn: String,
        ps: ParsedSources,
        sourceRoots: List<File>,
        preview: Boolean,
    ): MoveClassResult {
        val oldPackage = className.substringBeforeLast(".")
        val newPackage = newFqcn.substringBeforeLast(".")
        val sourceFileName = className.substringAfterLast(".").removeSuffix("Kt")
        val targetFileName = newFqcn.substringAfterLast(".").removeSuffix("Kt")

        var movedFilePath: String? = null
        var newFilePath: String? = null
        var sourceContent: String? = null

        for (sourceFile in ps.sources) {
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            if (isTargetClassFile(filePath, oldPackage, sourceFileName)) {
                movedFilePath = filePath
                sourceContent = sourceFile.printAll()
                newFilePath = computeNewFilePath(filePath, oldPackage, sourceFileName, newPackage, targetFileName, ps.sourceRoots)
                break
            }
        }

        if (sourceContent == null) return MoveClassResult(emptyList())

        val declaredClasses = extractDeclaredClassNames(sourceContent)

        val changes = mutableMapOf<String, RenameChange>()

        for (declaredClass in declaredClasses) {
            val oldFqcn = "$oldPackage.$declaredClass"
            val newClassFqcn = "$newPackage.$declaredClass"
            for (change in retargetChanges(ps, oldFqcn, newClassFqcn)) {
                val existing = changes[change.filePath]
                changes[change.filePath] = if (existing != null) {
                    RenameChange(change.filePath, existing.before, change.after)
                } else {
                    change
                }
            }
        }

        val movedNames = declaredClasses.toSet() + extractTopLevelNames(sourceContent)

        val allChanges = replacePackageImports(
            changes, ps, oldPackage, newPackage, movedFilePath, sourceContent, movedNames,
        )

        if (!preview) {
            applyChanges(allChanges, movedFilePath, newFilePath)
        }

        return MoveClassResult(allChanges, movedFilePath, newFilePath, targetFileWarnings(newFilePath, movedFilePath))
    }

    private fun moveMultiClassFile(
        oldPackage: String,
        newPackage: String,
        movedFilePath: String,
        movedSource: String,
        allClasses: List<String>,
        ps: ParsedSources,
        sourceRoots: List<File>,
        preview: Boolean,
        newFilePath: String?,
    ): MoveClassResult {
        val changes = mutableMapOf<String, RenameChange>()

        for (declaredClass in allClasses) {
            val oldFqcn = "$oldPackage.$declaredClass"
            val newFqcn = "$newPackage.$declaredClass"
            for (change in retargetChanges(ps, oldFqcn, newFqcn)) {
                val existing = changes[change.filePath]
                changes[change.filePath] = if (existing != null) {
                    RenameChange(change.filePath, existing.before, change.after)
                } else {
                    change
                }
            }
        }

        val movedNames = allClasses.toSet() + extractTopLevelNames(movedSource)

        val allChanges = replacePackageImports(
            changes, ps, oldPackage, newPackage, movedFilePath, movedSource, movedNames,
        )

        if (!preview) {
            applyChanges(allChanges, movedFilePath, newFilePath)
        }

        return MoveClassResult(allChanges, movedFilePath, newFilePath, targetFileWarnings(newFilePath, movedFilePath))
    }

    /**
     * PSI-based replacement for a single `ChangeType(oldFqcn, newFqcn)` run: retargets every reference
     * to the moved type across [ps]'s sources, returned as [RenameChange]s keyed by path. Does not touch
     * the declaring file's package declaration — callers apply that textually (see [rewritePackageDecl]).
     */
    private fun retargetChanges(ps: ParsedSources, oldFqcn: String, newFqcn: String): List<RenameChange> =
        KotlinTypeReferenceRewriter.retargetAcrossSources(ps.sources, oldFqcn, newFqcn).map { (path, after) ->
            RenameChange(path, ps.sources.first { it.path == path }.content, after)
        }

    private fun rewritePackageDecl(content: String, oldPackage: String, newPackage: String): String =
        content.replace("package $oldPackage", "package $newPackage")

    /**
     * When a type moves out of [oldPackage], files that *stayed* in [oldPackage] and referenced it
     * unqualified now reference a type that lives elsewhere — they need an explicit import of its new
     * location added. (OpenRewrite's `ChangeType` did this implicitly; the PSI retarget only rewrites
     * existing references, it doesn't manage imports.) Reuses [addMissingImportsForSiblings] by treating
     * the moved type as a "sibling" now living in [newPackage].
     */
    private fun importMovedTypeInFormerSamePackage(
        ps: ParsedSources,
        changes: MutableList<RenameChange>,
        oldPackage: String,
        newPackage: String,
        newSimpleName: String,
        movedFilePath: String?,
    ) {
        if (oldPackage == newPackage) return
        for (sourceFile in ps.sources) {
            val path = sourceFile.path
            if (path == movedFilePath) continue
            if (!isInPackage(sourceFile.content, oldPackage)) continue
            val idx = changes.indexOfFirst { it.filePath == path }
            val current = if (idx >= 0) changes[idx].after else sourceFile.content
            val updated = addMissingImportsForSiblings(current, newPackage, setOf(newSimpleName))
            if (updated == current) continue
            val original = if (idx >= 0) changes[idx].before else sourceFile.content
            if (idx >= 0) changes[idx] = RenameChange(path, original, updated) else changes.add(RenameChange(path, original, updated))
        }
    }

    private fun replacePackageImports(
        existingChanges: Map<String, RenameChange>,
        ps: ParsedSources,
        oldPackage: String,
        newPackage: String,
        movedFilePath: String?,
        movedFileContent: String,
        movedNames: Set<String> = emptySet(),
    ): List<RenameChange> {
        val changes = existingChanges.toMutableMap()
        val oldImportPrefix = "import $oldPackage."
        val newImportPrefix = "import $newPackage."

        for (sourceFile in ps.sources) {
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            if (filePath == movedFilePath) continue

            val currentContent = changes[filePath]?.after ?: sourceFile.printAll()
            if (!currentContent.contains(oldImportPrefix)) continue

            val updatedContent = currentContent.lines().joinToString("\n") { line ->
                if (line.startsWith(oldImportPrefix)) {
                    val importedName = line.removePrefix(oldImportPrefix).trimEnd()
                    if (movedNames.isEmpty() || importedName in movedNames) {
                        line.replace(oldImportPrefix, newImportPrefix)
                    } else {
                        line
                    }
                } else {
                    line
                }
            }

            if (updatedContent != currentContent) {
                val originalContent = changes[filePath]?.before ?: sourceFile.printAll()
                changes[filePath] = RenameChange(filePath, originalContent, updatedContent)
            }
        }

        if (movedFilePath != null) {
            val currentContent = changes[movedFilePath]?.after ?: movedFileContent
            val updatedContent = currentContent.replace(
                "package $oldPackage",
                "package $newPackage",
            )
            if (updatedContent != currentContent || !changes.containsKey(movedFilePath)) {
                val originalContent = changes[movedFilePath]?.before ?: movedFileContent
                changes[movedFilePath] = RenameChange(movedFilePath, originalContent, updatedContent)
            }
        }

        return changes.values.toList()
    }

    private fun applyChanges(
        changes: List<RenameChange>,
        movedFilePath: String?,
        newFilePath: String?,
    ) {
        for (change in changes) {
            File(change.filePath).writeText(change.after)
        }
        if (movedFilePath != null && newFilePath != null && movedFilePath != newFilePath) {
            val newFile = File(newFilePath)
            newFile.parentFile.mkdirs()
            Files.move(File(movedFilePath).toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun computeNewFilePath(
        filePath: String,
        oldPackage: String,
        oldFileName: String,
        newPackage: String,
        newFileName: String,
        sourceRoots: List<File>,
    ): String? {
        val newDir = newPackage.replace(".", File.separator)
        for (root in sourceRoots) {
            if (filePath.startsWith(root.absolutePath)) {
                return File(root, "$newDir/$newFileName.kt").absolutePath
            }
        }
        val oldDir = oldPackage.replace(".", File.separator)
        val expectedSuffix = "$oldDir${File.separator}$oldFileName.kt"
        if (filePath.endsWith(expectedSuffix)) {
            val baseDir = filePath.removeSuffix(expectedSuffix)
            return "$baseDir$newDir${File.separator}$newFileName.kt"
        }
        return null
    }

    private fun isTargetClassFile(filePath: String, oldPackage: String, simpleClassName: String): Boolean {
        val expectedSuffix = oldPackage.replace(".", File.separator) + File.separator + "$simpleClassName.kt"
        return filePath.endsWith(expectedSuffix)
    }

    private fun targetFileWarnings(newFilePath: String?, movedFilePath: String?): List<String> {
        if (newFilePath == null || movedFilePath == null || newFilePath == movedFilePath) return emptyList()
        val targetFile = File(newFilePath)
        if (!targetFile.exists()) return emptyList()
        return listOf(
            "WARNING: Target file already exists at '$newFilePath'. " +
                "The move will overwrite it. Consider manually merging the class into the existing file instead.",
        )
    }

    private fun isInPackage(source: String, packageName: String): Boolean =
        source.contains("package $packageName")

    private fun declaresClass(source: String, className: String): Boolean =
        extractDeclaredClassNames(source).contains(className)

    private val CLASS_DECLARATION_PATTERN = Regex(
        """^\s*(?:(?:data|sealed|enum|abstract|open|inner|value|annotation)\s+)*(?:class|interface|object)\s+(\w+)""",
        RegexOption.MULTILINE,
    )

    internal fun extractDeclaredClassNames(source: String): List<String> =
        CLASS_DECLARATION_PATTERN.findAll(source).map { it.groupValues[1] }.toList()

    private val TOP_LEVEL_FUN_PATTERN = Regex(
        """^(?:internal\s+|private\s+|public\s+)?fun\s+(?:<[^>]+>\s+)?(?:[\w.]+\.)?(\w+)""",
        RegexOption.MULTILINE,
    )

    private val TOP_LEVEL_VAL_PATTERN = Regex(
        """^(?:internal\s+|private\s+|public\s+|const\s+)*(?:val|var)\s+(\w+)""",
        RegexOption.MULTILINE,
    )

    internal fun extractTopLevelNames(source: String): Set<String> {
        val funs = TOP_LEVEL_FUN_PATTERN.findAll(source).map { it.groupValues[1] }
        val vals = TOP_LEVEL_VAL_PATTERN.findAll(source).map { it.groupValues[1] }
        return (funs + vals).toSet()
    }

    internal fun isKtFacadeName(className: String): Boolean {
        val simpleName = className.substringAfterLast(".")
        return simpleName.endsWith("Kt") && simpleName.length > 2
    }

    private fun findSiblingClassNames(ps: ParsedSources, packageName: String, excludeName: String): Set<String> {
        val siblings = mutableSetOf<String>()
        for (sourceFile in ps.sources) {
            val content = sourceFile.printAll()
            val pkgMatch = Regex("""^package\s+(\S+)""", RegexOption.MULTILINE).find(content)
            if (pkgMatch?.groupValues?.get(1) == packageName) {
                siblings.addAll(extractDeclaredClassNames(content))
            }
        }
        siblings.remove(excludeName)
        return siblings
    }

    /**
     * [packageOverrides] lets a caller redirect specific sibling names to a package other than
     * [oldPackage] — needed by [runBatchedMoves], where a "sibling" may itself be moving
     * elsewhere in the same batch, so importing it from [oldPackage] would point at a package
     * that no longer contains it.
     */
    internal fun addMissingImportsForSiblings(
        content: String,
        oldPackage: String,
        siblingNames: Set<String>,
        packageOverrides: Map<String, String> = emptyMap(),
    ): String {
        if (siblingNames.isEmpty()) return content

        // Find which sibling names are referenced in the file (as word boundaries, not in imports/package lines)
        val existingImports = content.lines()
            .filter { it.trimStart().startsWith("import ") }
            .map { it.trimStart().removePrefix("import ").trim() }
            .toSet()

        val referencedSiblings = siblingNames.filter { name ->
            val targetPackage = packageOverrides[name] ?: oldPackage
            val alreadyImported = existingImports.contains("$targetPackage.$name")
            if (alreadyImported) return@filter false
            // Check if the name appears as a word boundary (type reference) in non-import, non-package lines
            val pattern = Regex("""\b${Regex.escape(name)}\b""")
            content.lines().any { line ->
                val trimmed = line.trimStart()
                !trimmed.startsWith("package ") && !trimmed.startsWith("import ") && pattern.containsMatchIn(line)
            }
        }.sorted()

        if (referencedSiblings.isEmpty()) return content

        val newImports = referencedSiblings.map { "import ${packageOverrides[it] ?: oldPackage}.$it" }

        // Insert imports after the package declaration (and existing imports)
        val lines = content.lines().toMutableList()
        val lastImportIdx = lines.indexOfLast { it.trimStart().startsWith("import ") }
        val insertIdx = if (lastImportIdx >= 0) lastImportIdx + 1 else {
            val packageIdx = lines.indexOfFirst { it.trimStart().startsWith("package ") }
            if (packageIdx >= 0) packageIdx + 2 else 0 // after package + blank line
        }

        // Add blank line before imports if needed
        val importsToInsert = if (lastImportIdx < 0 && insertIdx > 0 && lines.getOrNull(insertIdx - 1)?.isNotBlank() == true) {
            listOf("") + newImports
        } else {
            newImports
        }

        lines.addAll(insertIdx, importsToInsert)
        return lines.joinToString("\n")
    }

    private fun updateTopLevelImportsInConsumers(
        ps: ParsedSources,
        changes: MutableList<RenameChange>,
        oldPackage: String,
        newPackage: String,
        movedFilePath: String,
        topLevelNames: Set<String>,
    ) {
        val oldImportPrefix = "import $oldPackage."
        for (sourceFile in ps.sources) {
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            if (filePath == movedFilePath) continue

            val existingChangeIdx = changes.indexOfFirst { it.filePath == filePath }
            val currentContent = if (existingChangeIdx >= 0) changes[existingChangeIdx].after else sourceFile.printAll()
            if (!currentContent.contains(oldImportPrefix)) continue

            val updatedContent = currentContent.lines().joinToString("\n") { line ->
                if (line.startsWith(oldImportPrefix)) {
                    val importedName = line.removePrefix(oldImportPrefix).trimEnd()
                    if (importedName in topLevelNames) {
                        line.replace(oldImportPrefix, "import $newPackage.")
                    } else {
                        line
                    }
                } else {
                    line
                }
            }

            if (updatedContent != currentContent) {
                val originalContent = if (existingChangeIdx >= 0) changes[existingChangeIdx].before else sourceFile.printAll()
                if (existingChangeIdx >= 0) {
                    changes[existingChangeIdx] = RenameChange(filePath, originalContent, updatedContent)
                } else {
                    changes.add(RenameChange(filePath, originalContent, updatedContent))
                }
            }
        }
    }

    /** Map-keyed twin of [updateTopLevelImportsInConsumers], for [runBatchedMoves]'s shared changes map. */
    private fun updateTopLevelImportsInConsumersMap(
        ps: ParsedSources,
        changes: MutableMap<String, RenameChange>,
        oldPackage: String,
        newPackage: String,
        movedFilePath: String,
        topLevelNames: Set<String>,
    ) {
        val oldImportPrefix = "import $oldPackage."
        for (sourceFile in ps.sources) {
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            if (filePath == movedFilePath) continue

            val currentContent = changes[filePath]?.after ?: sourceFile.printAll()
            if (!currentContent.contains(oldImportPrefix)) continue

            val updatedContent = currentContent.lines().joinToString("\n") { line ->
                if (line.startsWith(oldImportPrefix)) {
                    val importedName = line.removePrefix(oldImportPrefix).trimEnd()
                    if (importedName in topLevelNames) {
                        line.replace(oldImportPrefix, "import $newPackage.")
                    } else {
                        line
                    }
                } else {
                    line
                }
            }

            if (updatedContent != currentContent) {
                val originalContent = changes[filePath]?.before ?: sourceFile.printAll()
                changes[filePath] = RenameChange(filePath, originalContent, updatedContent)
            }
        }
    }
}
