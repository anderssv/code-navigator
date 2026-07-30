package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.navigation.relations.callgraph.UsageScanner
import no.f12.codenavigator.navigation.relations.callgraph.UsageSite
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File

data class SafeDeleteResult(
    val deleted: Boolean,
    val changes: List<RenameChange> = emptyList(),
    val usages: List<UsageSite> = emptyList(),
    val reason: String? = null,
) {
    fun toJson(): String {
        val deletedJson = "\"deleted\":$deleted"
        val changesJson = "\"changes\":${changesToJson(changes)}"
        val reasonJson = reason?.let { ""","reason":"${jsonEscape(it)}"""" } ?: ""
        val usagesJson = if (usages.isNotEmpty()) {
            val items = usages.joinToString(",", "[", "]") { u ->
                """{"callerClass":"${jsonEscape(u.callerClass.toString())}","callerMethod":"${jsonEscape(u.callerMethod)}","sourceFile":"${jsonEscape(u.sourceFile)}"}"""
            }
            ""","usages":$items"""
        } else ""
        return """{$deletedJson,$changesJson$reasonJson$usagesJson}"""
    }

    companion object {
        fun fromJson(json: String): SafeDeleteResult {
            val obj = parseJsonObject(json)
            val deleted = obj["deleted"] as Boolean
            val changes = changesFromJson(obj)
            val reason = obj["reason"] as? String
            return SafeDeleteResult(deleted = deleted, changes = changes, reason = reason)
        }
    }
}

object SafeDeleteRewriter {

    fun delete(
        sourceRoots: List<File>,
        classDirectories: List<File>,
        className: String,
        methodName: String? = null,
        preview: Boolean = false,
    ): SafeDeleteResult {
        val sourceFiles = sourceRoots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        if (sourceFiles.isEmpty()) return SafeDeleteResult(deleted = false, reason = "No source files found")

        // Check for usages via bytecode scanning
        val scanResult = if (methodName != null) {
            UsageScanner.scan(classDirectories, ownerClass = className, method = methodName)
        } else {
            UsageScanner.scan(classDirectories, type = className)
        }
        val usages = scanResult.data

        // Filter out self-references (usages from within the target class itself)
        val externalUsages = usages.filter { it.callerClass.toString() != className }

        if (externalUsages.isNotEmpty()) {
            return SafeDeleteResult(
                deleted = false,
                usages = externalUsages,
                reason = "Cannot delete: ${externalUsages.size} usage(s) found",
            )
        }

        // Find and delete the declaration via PSI
        return deleteDeclaration(sourceFiles, className, methodName, preview)
    }

    private fun deleteDeclaration(
        sourceFiles: List<File>,
        className: String,
        methodName: String?,
        preview: Boolean,
    ): SafeDeleteResult {
        return withKotlinPsiFactory("safe-delete-target") { psiFactory ->
            for (file in sourceFiles) {
                val content = file.readText()
                val ktFile = psiFactory.createFile(file.name, content)
                val filePackage = ktFile.packageFqName.asString()

                if (methodName != null) {
                    val result = deleteMethod(ktFile, content, file, filePackage, className, methodName, preview)
                    if (result != null) return result
                } else {
                    val result = deleteClass(ktFile, content, file, filePackage, className, preview)
                    if (result != null) return result
                }
            }

            SafeDeleteResult(deleted = false, reason = "Declaration not found: $className${methodName?.let { ".$it" } ?: ""}")
        }
    }

    private fun deleteClass(
        ktFile: KtFile,
        content: String,
        file: File,
        filePackage: String,
        className: String,
        preview: Boolean,
    ): SafeDeleteResult? {
        val classDecl = ktFile.collectDescendantsOfType<KtClass>()
            .firstOrNull { matchesFqn(buildClassFqn(filePackage, it), className) } ?: return null

        val startOffset = classDecl.textRange.startOffset
        val endOffset = classDecl.textRange.endOffset

        // Remove the class declaration and any trailing whitespace/newlines
        val before = content.substring(0, startOffset)
        val after = content.substring(endOffset)
        val result = (before.trimEnd('\n', ' ') + "\n" + after.trimStart('\n')).trim() + "\n"

        if (!preview) {
            file.writeText(result)
        }

        return SafeDeleteResult(
            deleted = true,
            changes = listOf(RenameChange(file.absolutePath, content, result)),
        )
    }

    private fun deleteMethod(
        ktFile: KtFile,
        content: String,
        file: File,
        filePackage: String,
        className: String,
        methodName: String,
        preview: Boolean,
    ): SafeDeleteResult? {
        val classDecl = ktFile.collectDescendantsOfType<KtClass>()
            .firstOrNull { matchesFqn(buildClassFqn(filePackage, it), className) } ?: return null

        val method = classDecl.declarations
            .filterIsInstance<KtNamedFunction>()
            .firstOrNull { it.name == methodName } ?: return null

        val startOffset = method.textRange.startOffset
        val endOffset = method.textRange.endOffset

        // Remove the method and surrounding whitespace
        val before = content.substring(0, startOffset)
        val after = content.substring(endOffset)
        val result = before.trimEnd(' ') + after.trimStart('\n')

        if (!preview) {
            file.writeText(result)
        }

        return SafeDeleteResult(
            deleted = true,
            changes = listOf(RenameChange(file.absolutePath, content, result)),
        )
    }
}
