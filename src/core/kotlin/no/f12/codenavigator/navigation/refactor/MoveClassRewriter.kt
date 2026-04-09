package no.f12.codenavigator.navigation.refactor

import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.SourceFile
import org.openrewrite.internal.InMemoryLargeSourceSet
import org.openrewrite.java.ChangeType
import org.openrewrite.kotlin.KotlinParser
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
        newPackage: String,
        newName: String? = null,
        classpath: List<Path> = emptyList(),
        preview: Boolean = false,
    ): MoveClassResult {
        val sourceFiles = collectSourceFiles(sourceRoots)
        if (sourceFiles.isEmpty()) return MoveClassResult(emptyList())

        val oldPackage = className.substringBeforeLast(".")
        val simpleClassName = className.substringAfterLast(".")
        val targetName = newName ?: simpleClassName
        val newFqcn = "$newPackage.$targetName"

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

        val recipe = ChangeType(className, newFqcn, null)
        val sourceSet = InMemoryLargeSourceSet(parsed)
        val recipeRun = recipe.run(sourceSet, ctx)
        val results = recipeRun.changeset.allResults

        val changes = mutableListOf<RenameChange>()
        var movedFilePath: String? = null
        var newFilePath: String? = null

        for (result in results) {
            val before = result.before?.printAll() ?: continue
            val after = result.after?.printAll() ?: continue
            if (before == after) continue

            val filePath = resolveOriginalPath(result.before!!, sourceRoots)
            changes.add(RenameChange(filePath, before, after))
        }

        for (sourceFile in parsed) {
            val filePath = resolveOriginalPath(sourceFile, sourceRoots)
            if (isTargetClassFile(filePath, oldPackage, simpleClassName)) {
                movedFilePath = filePath
                val newDir = newPackage.replace(".", File.separator)
                for (root in sourceRoots) {
                    if (filePath.startsWith(root.absolutePath)) {
                        newFilePath = File(root, "$newDir/$targetName.kt").absolutePath
                        break
                    }
                }
                break
            }
        }

        if (!preview) {
            for (change in changes) {
                File(change.filePath).writeText(change.after)
            }
            if (movedFilePath != null && newFilePath != null && movedFilePath != newFilePath) {
                val newFile = File(newFilePath)
                newFile.parentFile.mkdirs()
                Files.move(File(movedFilePath).toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        return MoveClassResult(changes, movedFilePath, newFilePath)
    }

    private fun isTargetClassFile(filePath: String, oldPackage: String, simpleClassName: String): Boolean {
        val expectedSuffix = oldPackage.replace(".", File.separator) + File.separator + "$simpleClassName.kt"
        return filePath.endsWith(expectedSuffix)
    }
}
