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
    /** Files moved along with the main one (old path -> new path), e.g. tests mirroring the moved class's package. */
    val alsoMoved: Map<String, String> = emptyMap(),
) {
    /** Every file this operation relocates on disk, old path -> new path. */
    val movedFiles: Map<String, String>
        get() = buildMap {
            if (movedFilePath != null && newFilePath != null && movedFilePath != newFilePath) put(movedFilePath, newFilePath)
            putAll(alsoMoved)
        }

    fun toJson(): String {
        val movedJson = movedFilePath?.let { ""","movedFilePath":"${jsonEscape(it)}"""" } ?: ""
        val newJson = newFilePath?.let { ""","newFilePath":"${jsonEscape(it)}"""" } ?: ""
        val warningsJson = if (warnings.isNotEmpty()) {
            ""","warnings":${warnings.joinToString(",", "[", "]") { "\"${jsonEscape(it)}\"" }}"""
        } else ""
        val errorJson = error?.let { ""","error":"${jsonEscape(it)}"""" } ?: ""
        val alsoJson = if (alsoMoved.isEmpty()) "" else {
            ""","alsoMovedFrom":${alsoMoved.keys.joinToString(",", "[", "]") { "\"${jsonEscape(it)}\"" }}""" +
                ""","alsoMovedTo":${alsoMoved.values.joinToString(",", "[", "]") { "\"${jsonEscape(it)}\"" }}"""
        }
        return """{"changes":${changesToJson(changes)}$movedJson$newJson$warningsJson$errorJson$alsoJson}"""
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
                alsoMoved = ((obj["alsoMovedFrom"] as? List<String>).orEmpty()).zip((obj["alsoMovedTo"] as? List<String>).orEmpty()).toMap(),
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
        /**
         * The exact file to move, when the caller already resolved one (cnavMoveFile does, from its path argument).
         * Without it the file is found from [className] alone, which is ambiguous whenever two files declare a class
         * with that short name; with it the path the caller asked for is used as given and never swapped for another.
         */
        movedFile: String? = null,
        /** Also move tests that mirror the class's package (`FooTest`, `FooTests`, `FooIT`, `FooSpec` in the same package). */
        moveTests: Boolean = false,
    ): MoveClassResult {
        val ps = parsedSources ?: run {
            val sourceFiles = collectSourceFiles(sourceRoots)
            if (sourceFiles.isEmpty()) return MoveClassResult(emptyList(), error = SourceScanMessages.noSources(sourceRoots))
            parseKotlinSources(sourceRoots, classpath)
        }
        if (ps.sources.isEmpty()) return MoveClassResult(emptyList(), error = SourceScanMessages.noSources(sourceRoots))

        val isKtFacade = isKtFacadeName(className)
        val oldPackage = className.substringBeforeLast(".")
        val newPackage = newFqcn.substringBeforeLast(".")
        val tests = if (isKtFacade || oldPackage == newPackage) emptyList() else
            mirrorTests(ps, oldPackage, className.substringAfterLast("."), movedFile)
        val chainTests = moveTests && tests.isNotEmpty()
        val mainPreview = preview || chainTests
        val result = if (isKtFacade) {
            moveKtFacade(className, newFqcn, ps, sourceRoots, mainPreview, classpath)
        } else {
            moveClass(className, newFqcn, ps, sourceRoots, mainPreview, allowMultiClass, classpath, movedFile)
        }
        if (result.error == null && result.changes.isEmpty() && result.movedFilePath == null) {
            return result.copy(error = SourceScanMessages.classNotFound(className, ps.sources.size, sourceRoots))
        }
        val withTests = when {
            result.error != null || tests.isEmpty() -> result
            !moveTests -> result.copy(warnings = result.warnings + tests.map { testStaysWarning(it, newPackage) })
            else -> moveTestsAlong(result, tests, className, newFqcn, ps, sourceRoots)
        }
        if (chainTests && !preview && withTests.error == null) applyMoves(withTests)
        return withTests.withNonKotlinReferenceWarnings(sourceRoots, className)
    }

    private val TEST_SUFFIXES = listOf("Test", "Tests", "IT", "Spec")

    private fun mirrorTests(ps: ParsedSources, oldPackage: String, simpleName: String, exclude: String?): List<SourceFileContent> {
        val names = TEST_SUFFIXES.map { "$simpleName$it.kt" }.toSet()
        return ps.sources.filter { it.path != exclude && File(it.path).name in names && isInPackage(it.content, oldPackage) }
    }

    private fun testStaysWarning(test: SourceFileContent, newPackage: String): String =
        "${File(test.path).name} stays in the old package; it only gets an import. To move it to $newPackage as well " +
            "(e.g. when tests must mirror production packages), add {p:move-tests=true}."

    /**
     * Moves each mirroring test after the main move, against the sources as the main move left them (in memory),
     * and merges everything into one result keyed by the files' original paths.
     */
    private fun moveTestsAlong(
        main: MoveClassResult,
        tests: List<SourceFileContent>,
        className: String,
        newFqcn: String,
        ps: ParsedSources,
        sourceRoots: List<File>,
    ): MoveClassResult {
        val originals = ps.sources.associate { it.path to it.content }
        val currentPathOf = mutableMapOf<String, String>()
        main.movedFiles.forEach { (from, to) -> currentPathOf[from] = to }
        val contents = originals.toMutableMap().apply { main.changes.forEach { put(it.filePath, it.after) } }
        val alsoMoved = mutableMapOf<String, String>()
        val warnings = main.warnings.toMutableList()
        val oldSimple = className.substringAfterLast(".")
        val newSimple = newFqcn.substringAfterLast(".")
        val oldPackage = className.substringBeforeLast(".")
        val newPackage = newFqcn.substringBeforeLast(".")

        for (test in tests) {
            val testName = File(test.path).nameWithoutExtension
            val inMemory = ParsedSources(contents.map { (path, content) -> SourceFileContent(currentPathOf[path] ?: path, content) }, ps.sourceRoots)
            val r = move(
                sourceRoots, "$oldPackage.$testName", "$newPackage.${newSimple + testName.removePrefix(oldSimple)}",
                preview = true, parsedSources = inMemory, allowMultiClass = true, movedFile = test.path,
            )
            if (r.error != null) return MoveClassResult(emptyList(), main.movedFilePath, main.newFilePath, error = "Moving ${File(test.path).name} along failed, nothing was written: ${r.error}")
            val originalPathOf = currentPathOf.entries.associate { (k, v) -> v to k }
            r.changes.forEach { contents[originalPathOf[it.filePath] ?: it.filePath] = it.after }
            r.movedFiles.forEach { (from, to) -> currentPathOf[from] = to; alsoMoved[from] = to }
            warnings += r.warnings
        }
        val changes = contents.filter { (path, content) -> originals[path] != content || path in currentPathOf }
            .map { (path, content) -> RenameChange(path, originals.getValue(path), content) }
        return MoveClassResult(changes, main.movedFilePath, main.newFilePath, warnings, alsoMoved = alsoMoved)
    }

    private fun applyMoves(result: MoveClassResult) {
        for (change in result.changes) File(change.filePath).writeText(change.after)
        for ((from, to) in result.movedFiles) {
            File(to).parentFile.mkdirs()
            Files.move(File(from).toPath(), File(to).toPath(), StandardCopyOption.REPLACE_EXISTING)
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
        if (sourceFiles.isEmpty()) return moves.map { MoveClassResult(emptyList(), error = SourceScanMessages.noSources(sourceRoots)) }
        val ps = parseKotlinSources(sourceRoots, classpath)
        if (ps.sources.isEmpty()) return moves.map { MoveClassResult(emptyList(), error = SourceScanMessages.noSources(sourceRoots)) }

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

            destinationCollisionError(newFilePath, movedFilePath)?.let {
                // Exclude the colliding move from the batch entirely so its consumer edits are never
                // computed (repointing consumers at a move that won't happen would break them).
                results[index] = MoveClassResult(emptyList(), movedFilePath, newFilePath, error = it)
                continue
            }

            batchable.add(BatchableMove(index, req.className, req.newFqcn, oldPackage, simpleClassName, newPackage, movedFilePath, newFilePath))
        }

        if (batchable.isNotEmpty()) {
            runBatchedMoves(ps, batchable, preview, results)
        }

        return moves.mapIndexed { index, req ->
            (results[index] ?: MoveClassResult(emptyList()))
                .withZeroChangeWarning(req.className)
                .withNonKotlinReferenceWarnings(sourceRoots, req.className)
        }
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

        // Populated below with paths where a former same-package caller got a brand-new import (never
        // had the old one to begin with) — the later attribution loop can't discover these on its own.
        val extraRelevantPaths = mutableMapOf<Int, Set<String>>()

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
                val updatedContent = removeSamePackageImports(
                    addMissingImportsForSiblings(movedContent, move.oldPackage, siblingNames, packageOverrides),
                    move.newPackage,
                )
                if (updatedContent != movedContent) {
                    val originalContent = sharedChanges[move.movedFilePath]?.before ?: movedContent
                    sharedChanges[move.movedFilePath] = RenameChange(move.movedFilePath, originalContent, updatedContent)
                }
            }

            val movedFileSource = ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == move.movedFilePath }?.printAll()
            val topLevelNames = movedFileSource?.let(::extractTopLevelNames).orEmpty()
            if (topLevelNames.isNotEmpty()) {
                updateTopLevelImportsInConsumersMap(ps, sharedChanges, move.oldPackage, move.newPackage, move.movedFilePath, topLevelNames)
            }

            // A former same-package caller referencing the moved class itself, or one of its
            // co-located top-level functions/properties, unqualified — no import to rewrite existed,
            // so the checks above never touch it. Same gap importMovedTypeInFormerSamePackage fixes
            // for the single-move paths.
            extraRelevantPaths[move.index] = importMovedTypeInFormerSamePackageMap(
                ps, sharedChanges, move.oldPackage, move.newPackage, setOf(move.simpleClassName) + topLevelNames, move.movedFilePath,
            )
        }

        for (move in batchable) {
            val relevantPaths = mutableSetOf(move.movedFilePath)
            relevantPaths += extraRelevantPaths[move.index].orEmpty()
            val oldImport = "import ${move.oldPackage}.${move.simpleClassName}"
            for ((filePath, change) in sharedChanges) {
                if (filePath == move.movedFilePath) continue
                if (change.before.contains(oldImport)) relevantPaths.add(filePath)
            }
            val changesForThisMove = sharedChanges.filterKeys { it in relevantPaths }.values.toList()
            results[move.index] = MoveClassResult(changesForThisMove, move.movedFilePath, move.newFilePath)
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
        classpath: List<Path> = emptyList(),
        forcedFilePath: String? = null,
    ): MoveClassResult {
        val oldPackage = className.substringBeforeLast(".")
        val simpleClassName = className.substringAfterLast(".")
        val newPackage = newFqcn.substringBeforeLast(".")
        val targetName = newFqcn.substringAfterLast(".")

        // Find the file containing the class
        val located = locateMovedFile(ps, sourceRoots, oldPackage, simpleClassName, newPackage, targetName, forcedFilePath)
        val foundByContent = located?.byContent ?: false
        var movedFilePath: String? = located?.path
        var newFilePath: String? = located?.newPath

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
                            append("The entire file moves together. Move the whole file: {u:move-file} {p:from-file=${relativeToSourceRoot(movedFilePath, ps.sourceRoots)}} {p:to-package=$newPackage}")
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
                    return moveMultiClassFile(oldPackage, newPackage, movedFilePath, movedSource, allClasses, ps, sourceRoots, preview, newFilePath, classpath)
                }
            }
        }

        val changes = retargetChanges(ps, className, newFqcn, classpath).toMutableList()

        // Check if standard-named file also has sibling classes
        if (movedFilePath != null && !foundByContent) {
            val movedSource = ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == movedFilePath }?.printAll()
            if (movedSource != null) {
                val allClasses = extractDeclaredClassNames(movedSource)
                if (allClasses.size > 1) {
                    return moveMultiClassFile(oldPackage, newPackage, movedFilePath, movedSource, allClasses, ps, sourceRoots, preview, newFilePath, classpath)
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
                    for (change in retargetChanges(ps, oldSiblingFqcn, newSiblingFqcn, classpath)) {
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

        importMovedTypeInFormerSamePackage(
            ps,
            changes,
            oldPackage,
            newPackage,
            setOf(targetName) + movedFilePath?.let { path ->
                ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == path }?.printAll()?.let(::extractTopLevelNames)
            }.orEmpty(),
            movedFilePath,
        )

        // Add imports for former same-package classes referenced by the moved file
        if (movedFilePath != null) {
            val siblingNames = findSiblingClassNames(ps, oldPackage, simpleClassName)
            val movedChangeIdx = changes.indexOfFirst { it.filePath == movedFilePath }
            val movedContent = if (movedChangeIdx >= 0) changes[movedChangeIdx].after else {
                ps.sources.firstOrNull { resolveOriginalPath(it, ps.sourceRoots) == movedFilePath }?.printAll()
            }
            if (movedContent != null) {
                val updatedContent = removeSamePackageImports(
                    addMissingImportsForSiblings(movedContent, oldPackage, siblingNames),
                    newPackage,
                )
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

        destinationCollisionError(newFilePath, movedFilePath)?.let {
            return MoveClassResult(emptyList(), movedFilePath, newFilePath, error = it)
        }

        val orderedChanges = ImportOrdering.placeNewImports(changes)
        if (!preview) {
            applyChanges(orderedChanges, movedFilePath, newFilePath)
        }

        return MoveClassResult(orderedChanges, movedFilePath, newFilePath)
    }

    private fun moveKtFacade(
        className: String,
        newFqcn: String,
        ps: ParsedSources,
        sourceRoots: List<File>,
        preview: Boolean,
        classpath: List<Path> = emptyList(),
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
            for (change in retargetChanges(ps, oldFqcn, newClassFqcn, classpath)) {
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
        ).toMutableList()

        // replacePackageImports only rewrites an EXISTING import in a consumer file — a former
        // same-package caller that referenced a top-level function/property unqualified (no import
        // needed while both files shared a package) has nothing there to rewrite, and was silently
        // left with a now-broken reference. Add the missing import using the same movedNames set.
        importMovedTypeInFormerSamePackage(ps, allChanges, oldPackage, newPackage, movedNames, movedFilePath)

        destinationCollisionError(newFilePath, movedFilePath)?.let {
            return MoveClassResult(emptyList(), movedFilePath, newFilePath, error = it)
        }

        val orderedChanges = ImportOrdering.placeNewImports(allChanges)
        if (!preview) {
            applyChanges(orderedChanges, movedFilePath, newFilePath)
        }

        return MoveClassResult(orderedChanges, movedFilePath, newFilePath)
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
        classpath: List<Path> = emptyList(),
    ): MoveClassResult {
        val changes = mutableMapOf<String, RenameChange>()

        for (declaredClass in allClasses) {
            val oldFqcn = "$oldPackage.$declaredClass"
            val newFqcn = "$newPackage.$declaredClass"
            for (change in retargetChanges(ps, oldFqcn, newFqcn, classpath)) {
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
        ).toMutableList()

        // Same gap as moveKtFacade: replacePackageImports only rewrites an EXISTING import, so a
        // former same-package caller with no import to rewrite is left with a broken reference.
        importMovedTypeInFormerSamePackage(ps, allChanges, oldPackage, newPackage, movedNames, movedFilePath)

        destinationCollisionError(newFilePath, movedFilePath)?.let {
            return MoveClassResult(emptyList(), movedFilePath, newFilePath, error = it)
        }

        val orderedChanges = ImportOrdering.placeNewImports(allChanges)
        if (!preview) {
            applyChanges(orderedChanges, movedFilePath, newFilePath)
        }

        return MoveClassResult(orderedChanges, movedFilePath, newFilePath)
    }

    /**
     * PSI-based replacement for a single `ChangeType(oldFqcn, newFqcn)` run: retargets every reference
     * to the moved type across [ps]'s sources, returned as [RenameChange]s keyed by path. Does not touch
     * the declaring file's package declaration — callers apply that textually (see [rewritePackageDecl]).
     *
     * [classpath] enables K1 semantic resolution for the rename pass (confirms simple-name references
     * actually resolve to the moved type); the single-move paths pass it since their sources still match
     * disk. The in-memory batch path passes none and stays on the heuristic.
     */
    private fun retargetChanges(ps: ParsedSources, oldFqcn: String, newFqcn: String, classpath: List<Path> = emptyList()): List<RenameChange> =
        KotlinTypeReferenceRewriter.retargetAcrossSources(ps.sources, oldFqcn, newFqcn, ps.sourceRoots, classpath).map { (path, after) ->
            RenameChange(path, ps.sources.first { it.path == path }.content, after)
        }

    /** Rewrites the file's own package line, only when it is exactly [oldPackage] (not a subpackage of it). */
    private fun rewritePackageDecl(content: String, oldPackage: String, newPackage: String): String =
        Regex("""(?m)^(\s*package\s+)${Regex.escape(oldPackage)}(\s*)$""").replace(content) { m -> m.groupValues[1] + newPackage + m.groupValues[2] }

    private val NON_KOTLIN_SOURCE_EXTENSIONS = setOf("java", "groovy", "scala")

    /**
     * Appends a warning naming non-Kotlin source files (`.java`/`.groovy`/`.scala`) that appear to
     * reference the moved type. The rewriter only edits `.kt` files, so a Java consumer of a moved
     * Kotlin class is left with a stale reference — surfacing it lets an agent fix those independently
     * instead of silently shipping broken cross-language code.
     */
    /**
     * A requested move that produced zero changes and no error means the class's source file was never
     * located/rewritten — its `package` declaration is left stale while consumers get repointed at the new
     * package, breaking compilation. Surface it instead of the silent `(0 files)` success. (A genuine move
     * always changes at least the moved file's own package line.)
     */
    private fun MoveClassResult.withZeroChangeWarning(className: String): MoveClassResult {
        if (changes.isNotEmpty() || error != null) return this
        return copy(
            warnings = warnings + "'$className' was requested for move but produced no changes — its source " +
                "file was not located or rewritten (its package declaration may be left stale). Verify manually.",
        )
    }

    private fun MoveClassResult.withNonKotlinReferenceWarnings(sourceRoots: List<File>, className: String): MoveClassResult {
        if (movedFilePath == null) return this
        val extra = nonKotlinReferenceWarnings(sourceRoots, className)
        return if (extra.isEmpty()) this else copy(warnings = warnings + extra)
    }

    private fun nonKotlinReferenceWarnings(sourceRoots: List<File>, className: String): List<String> {
        val simpleName = className.substringAfterLast(".")
        val oldPackage = className.substringBeforeLast(".")
        val wordBoundary = Regex("""\b${Regex.escape(simpleName)}\b""")
        val hits = sourceRoots.flatMap { root ->
            if (!root.exists()) return@flatMap emptyList()
            root.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in NON_KOTLIN_SOURCE_EXTENSIONS }
                .filter { file ->
                    val text = file.readText()
                    text.contains("$oldPackage.$simpleName") || // FQN or `import old.pkg.Name`
                        (isInPackage(text, oldPackage) && wordBoundary.containsMatchIn(text)) // same-package unqualified use
                }
                .map { it.absolutePath }
                .toList()
        }
        if (hits.isEmpty()) return emptyList()
        return listOf(
            "${hits.size} non-Kotlin source file(s) may reference '$simpleName' but were NOT updated — " +
                "this tool only rewrites Kotlin (.kt) files. Review and fix these manually: ${hits.sorted().joinToString(", ")}",
        )
    }

    /**
     * When a type moves out of [oldPackage], files that *stayed* in [oldPackage] and referenced it
     * unqualified now reference a type that lives elsewhere — they need an explicit import of its new
     * location added. (OpenRewrite's `ChangeType` did this implicitly; the PSI retarget only rewrites
     * existing references, it doesn't manage imports.) Reuses [addMissingImportsForSiblings] by treating
     * the moved type as a "sibling" now living in [newPackage].
     *
     * [newSimpleNames] is the moved class's simple name *plus* any top-level function/property names
     * declared in the same file — a pure top-level file (no classes) has no "class name" a same-package
     * caller could reference at all, only its actual declared function/property names, which is exactly
     * what an unqualified call site like `checkmarkSvg()` looks like in source. Tracking only the
     * synthetic Kt-facade name here would never match such a call, silently leaving the caller's missing
     * import unadded.
     */
    private fun importMovedTypeInFormerSamePackage(
        ps: ParsedSources,
        changes: MutableList<RenameChange>,
        oldPackage: String,
        newPackage: String,
        newSimpleNames: Set<String>,
        movedFilePath: String?,
    ) {
        if (oldPackage == newPackage) return
        for (sourceFile in ps.sources) {
            val path = sourceFile.path
            if (path == movedFilePath) continue
            if (!isInPackage(sourceFile.content, oldPackage)) continue
            val idx = changes.indexOfFirst { it.filePath == path }
            val current = if (idx >= 0) changes[idx].after else sourceFile.content
            val updated = addMissingImportsForSiblings(current, newPackage, newSimpleNames)
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
            val leftBehind = namesDeclaredInPackage(ps, oldPackage, movedFilePath) - movedNames
            val updatedContent = if (oldPackage == newPackage) currentContent else addMissingImportsForSiblings(
                rewritePackageDecl(currentContent, oldPackage, newPackage), oldPackage, leftBehind,
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

    /**
     * A move whose destination file already exists (and isn't the source) would silently overwrite a
     * *different* declaration living there — data loss. This is a hard stop, not a warning: the caller
     * returns an error result and applies no writes. Merge manually, or move to a different name/package.
     */
    private fun destinationCollisionError(newFilePath: String?, movedFilePath: String?): String? {
        if (newFilePath == null || movedFilePath == null || newFilePath == movedFilePath) return null
        if (!File(newFilePath).exists()) return null
        return "Cannot move: the target file '$newFilePath' already exists and would be overwritten " +
            "(a different declaration already lives there). No changes were made. Merge the class into that " +
            "file manually, or move it to a different name/package."
    }

    private val PACKAGE_DECLARATION = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

    /** The package a source file declares, or null for the default package. */
    internal fun packageOf(source: String): String? = PACKAGE_DECLARATION.find(source)?.groupValues?.get(1)

    /**
     * True only for the exact package. `source.contains("package a.b")` was true for `package a.b.c` too, which made
     * every subpackage file look like it was in the package: it could be picked as the file to move, have its package
     * line rewritten (`a.b.c` -> `new.c`), and be handed imports meant for the old package's own files.
     */
    private fun isInPackage(source: String, packageName: String): Boolean = packageOf(source) == packageName

    private fun declaresClass(source: String, className: String): Boolean =
        extractDeclaredClassNames(source).contains(className)

    /** The path as the user would type it for from-file: relative to the project (the source root's module dir). */
    private fun relativeToSourceRoot(path: String, sourceRoots: List<File>): String {
        val root = sourceRoots.firstOrNull { path.startsWith(it.absolutePath + File.separator) } ?: return path
        val projectDir = root.absoluteFile.parentFile?.parentFile?.parentFile ?: return path
        return File(path).relativeTo(projectDir).invariantSeparatorsPath
    }

    private data class LocatedFile(val path: String, val newPath: String?, val byContent: Boolean)

    /**
     * Finds the file to move. A [forcedFilePath] is used as given. Otherwise: the file named after the class in the
     * package's directory, then any file in exactly that package that declares the class at top level — a nested
     * `Outer.Name` has a different fully qualified name and is not the class being asked for.
     */
    private fun locateMovedFile(
        ps: ParsedSources,
        sourceRoots: List<File>,
        oldPackage: String,
        simpleClassName: String,
        newPackage: String,
        targetName: String,
        forcedFilePath: String?,
    ): LocatedFile? {
        if (forcedFilePath != null) {
            val byContent = !isTargetClassFile(forcedFilePath, oldPackage, simpleClassName)
            val fileName = File(forcedFilePath).nameWithoutExtension
            val newPath = if (byContent) {
                computeNewFilePath(forcedFilePath, oldPackage, fileName, newPackage, fileName, sourceRoots)
            } else {
                computeNewFilePath(forcedFilePath, oldPackage, simpleClassName, newPackage, targetName, sourceRoots)
            }
            return LocatedFile(forcedFilePath, newPath, byContent)
        }
        for (sourceFile in ps.sources) {
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            if (isTargetClassFile(filePath, oldPackage, simpleClassName)) {
                return LocatedFile(filePath, computeNewFilePath(filePath, oldPackage, simpleClassName, newPackage, targetName, sourceRoots), false)
            }
        }
        for (sourceFile in ps.sources) {
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            val content = sourceFile.printAll()
            if (isInPackage(content, oldPackage) && extractTopLevelClassNames(content).contains(simpleClassName)) {
                val fileName = File(filePath).nameWithoutExtension
                return LocatedFile(filePath, computeNewFilePath(filePath, oldPackage, fileName, newPackage, fileName, sourceRoots), true)
            }
        }
        return null
    }

    private val CLASS_DECLARATION_PATTERN = Regex(
        // Optional same-line annotations, then any order of visibility + class modifiers. Visibility
        // modifiers (internal/private/…) were previously missing, so `internal class RedisCache` was
        // undetected — the file couldn't be located and its move silently produced zero changes.
        """^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|private|internal|protected|data|sealed|enum|abstract|open|inner|value|annotation|final)\s+)*(?:class|interface|object)\s+(\w+)""",
        RegexOption.MULTILINE,
    )

    internal fun extractDeclaredClassNames(source: String): List<String> =
        CLASS_DECLARATION_PATTERN.findAll(source).map { it.groupValues[1] }.toList()

    /** Like [extractDeclaredClassNames] but only declarations at the start of a line, i.e. not nested ones. */
    internal fun extractTopLevelClassNames(source: String): List<String> =
        TOP_LEVEL_CLASS_DECLARATION_PATTERN.findAll(source).map { it.groupValues[1] }.toList()

    private val TOP_LEVEL_CLASS_DECLARATION_PATTERN = Regex(
        """^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|private|internal|protected|data|sealed|enum|abstract|open|inner|value|annotation|final|fun)\s+)*(?:class|interface|object)\s+(\w+)""",
        RegexOption.MULTILINE,
    )

    // Private declarations are not importable and are skipped by not allowing `private` among the modifiers.
    // The receiver (`Actor?.`, `List<Foo>.`) is consumed so the captured name is the function itself.
    private val TOP_LEVEL_FUN_PATTERN = Regex(
        """^(?:(?:internal|public|inline|tailrec|suspend|operator|infix|external)\s+)*fun\s+(?:<[^>]+>\s+)?(?:[\w.<>?, ]+\.)?(\w+)\s*\(""",
        RegexOption.MULTILINE,
    )

    private val TOP_LEVEL_VAL_PATTERN = Regex(
        """^(?:(?:internal|public|const|lateinit|inline)\s+)*(?:val|var)\s+(?:<[^>]+>\s+)?(?:[\w.<>?, ]+\.)?(\w+)""",
        RegexOption.MULTILINE,
    )

    /** Importable top-level names (classes, non-private functions and properties) declared by the other files in exactly [packageName]. */
    private fun namesDeclaredInPackage(ps: ParsedSources, packageName: String, excludePath: String): Set<String> =
        ps.sources.filter { resolveOriginalPath(it, ps.sourceRoots) != excludePath && isInPackage(it.printAll(), packageName) }
            .flatMapTo(mutableSetOf()) { extractTopLevelClassNames(it.printAll()) + extractTopLevelNames(it.printAll()) }

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
                siblings.addAll(extractTopLevelClassNames(content))
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
    /**
     * Drops the moved file's imports that point at its *new* package — they became same-package
     * references the moment the package declaration changed. Keeps aliased imports (`as`), which
     * still bind a name, and nested-class imports (`p.Outer.Inner`), which are not implicit.
     */
    internal fun removeSamePackageImports(content: String, packageName: String): String {
        if (packageName.isEmpty()) return content
        val prefix = "$packageName."
        val lines = content.lines()
        val kept = lines.filterNot { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("import ")) return@filterNot false
            val spec = trimmed.removePrefix("import ").trim()
            if (" as " in spec || !spec.startsWith(prefix)) return@filterNot false
            val rest = spec.removePrefix(prefix)
            rest == "*" || !rest.contains('.')
        }
        if (kept.size == lines.size) return content
        return collapseBlankLinesAfterPackage(kept).joinToString("\n")
    }

    /** Removing the whole import block leaves two blank lines under the package declaration. */
    private fun collapseBlankLinesAfterPackage(lines: List<String>): List<String> {
        val packageIdx = lines.indexOfFirst { it.trimStart().startsWith("package ") }
        if (packageIdx < 0) return lines
        var end = packageIdx + 1
        while (end < lines.size && lines[end].isBlank()) end++
        if (end - (packageIdx + 1) <= 1) return lines
        return lines.subList(0, packageIdx + 1) + listOf("") + lines.subList(end, lines.size)
    }

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

    /**
     * Map-keyed twin of [importMovedTypeInFormerSamePackage], for [runBatchedMoves]'s shared changes
     * map. [updateTopLevelImportsInConsumersMap] only rewrites an *existing* import; a former
     * same-package caller that referenced the moved class or one of its co-located top-level
     * functions/properties unqualified has no import to rewrite at all, and is otherwise left with a
     * silently broken reference — the exact same gap the single-move paths had.
     *
     * Returns the paths it touched, so the caller can attribute a brand-new same-package import to
     * this move's own result — the later attribution loop only recognizes a file as "relevant" to a
     * move when it already contained the *old* import string, which a same-package caller (needing a
     * new import, not a rewritten one) never had.
     */
    private fun importMovedTypeInFormerSamePackageMap(
        ps: ParsedSources,
        changes: MutableMap<String, RenameChange>,
        oldPackage: String,
        newPackage: String,
        newSimpleNames: Set<String>,
        movedFilePath: String?,
    ): Set<String> {
        if (oldPackage == newPackage) return emptySet()
        val touched = mutableSetOf<String>()
        for (sourceFile in ps.sources) {
            val path = resolveOriginalPath(sourceFile, ps.sourceRoots)
            if (path == movedFilePath) continue
            val current = changes[path]?.after ?: sourceFile.printAll()
            if (!isInPackage(current, oldPackage)) continue
            val updated = addMissingImportsForSiblings(current, newPackage, newSimpleNames)
            if (updated == current) continue
            val original = changes[path]?.before ?: sourceFile.printAll()
            changes[path] = RenameChange(path, original, updated)
            touched.add(path)
        }
        return touched
    }
}
