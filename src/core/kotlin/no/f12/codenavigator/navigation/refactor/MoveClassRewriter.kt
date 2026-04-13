package no.f12.codenavigator.navigation.refactor

import org.openrewrite.internal.InMemoryLargeSourceSet
import org.openrewrite.java.ChangeType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class MoveClassResult(
    val changes: List<RenameChange>,
    val movedFilePath: String? = null,
    val newFilePath: String? = null,
) {
    fun toJson(): String {
        val movedJson = movedFilePath?.let { ""","movedFilePath":"${jsonEscape(it)}"""" } ?: ""
        val newJson = newFilePath?.let { ""","newFilePath":"${jsonEscape(it)}"""" } ?: ""
        return """{"changes":${changesToJson(changes)}$movedJson$newJson}"""
    }

    companion object {
        fun fromJson(json: String): MoveClassResult {
            val obj = parseJsonObject(json)
            return MoveClassResult(
                changes = changesFromJson(obj),
                movedFilePath = obj["movedFilePath"] as? String,
                newFilePath = obj["newFilePath"] as? String,
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
            moveClass(className, newFqcn, ps, sourceRoots, preview)
        }
    }

    private fun moveClass(
        className: String,
        newFqcn: String,
        ps: ParsedSources,
        sourceRoots: List<File>,
        preview: Boolean,
    ): MoveClassResult {
        val oldPackage = className.substringBeforeLast(".")
        val simpleClassName = className.substringAfterLast(".")
        val newPackage = newFqcn.substringBeforeLast(".")
        val targetName = newFqcn.substringAfterLast(".")

        val recipe = ChangeType(className, newFqcn, null)
        val sourceSet = InMemoryLargeSourceSet(ps.sources)
        val recipeRun = recipe.run(sourceSet, ps.ctx)
        val results = recipeRun.changeset.allResults

        val changes = mutableListOf<RenameChange>()
        var movedFilePath: String? = null
        var newFilePath: String? = null

        for (result in results) {
            val before = result.before?.printAll() ?: continue
            val after = result.after?.printAll() ?: continue
            if (before == after) continue

            val filePath = resolveOriginalPath(result.before!!, ps.sourceRoots)
            changes.add(RenameChange(filePath, before, after))
        }

        for (sourceFile in ps.sources) {
            val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
            if (isTargetClassFile(filePath, oldPackage, simpleClassName)) {
                movedFilePath = filePath
                newFilePath = computeNewFilePath(filePath, oldPackage, simpleClassName, newPackage, targetName, sourceRoots)
                break
            }
        }

        if (!preview) {
            applyChanges(changes, movedFilePath, newFilePath)
        }

        return MoveClassResult(changes, movedFilePath, newFilePath)
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
            val recipe = ChangeType(oldFqcn, newClassFqcn, null)
            val sourceSet = InMemoryLargeSourceSet(ps.sources)
            val recipeRun = recipe.run(sourceSet, ps.ctx)
            for (result in recipeRun.changeset.allResults) {
                val before = result.before?.printAll() ?: continue
                val after = result.after?.printAll() ?: continue
                if (before == after) continue
                val filePath = resolveOriginalPath(result.before!!, ps.sourceRoots)
                val existing = changes[filePath]
                if (existing != null) {
                    changes[filePath] = RenameChange(filePath, existing.before, after)
                } else {
                    changes[filePath] = RenameChange(filePath, before, after)
                }
            }
        }

        val allChanges = replacePackageImports(
            changes, ps, oldPackage, newPackage, movedFilePath, sourceContent,
        )

        if (!preview) {
            applyChanges(allChanges, movedFilePath, newFilePath)
        }

        return MoveClassResult(allChanges, movedFilePath, newFilePath)
    }

    private fun replacePackageImports(
        existingChanges: Map<String, RenameChange>,
        ps: ParsedSources,
        oldPackage: String,
        newPackage: String,
        movedFilePath: String?,
        movedFileContent: String,
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
                    line.replace(oldImportPrefix, newImportPrefix)
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

    private val CLASS_DECLARATION_PATTERN = Regex(
        """^\s*(?:(?:data|sealed|enum|abstract|open|inner|value|annotation)\s+)*(?:class|interface|object)\s+(\w+)""",
        RegexOption.MULTILINE,
    )

    internal fun extractDeclaredClassNames(source: String): List<String> =
        CLASS_DECLARATION_PATTERN.findAll(source).map { it.groupValues[1] }.toList()

    internal fun isKtFacadeName(className: String): Boolean {
        val simpleName = className.substringAfterLast(".")
        return simpleName.endsWith("Kt") && simpleName.length > 2
    }
}
