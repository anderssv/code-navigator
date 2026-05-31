package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File

/**
 * PSI-based rename method rewriter. Replaces OpenRewrite for method renaming.
 *
 * Strategy (Phase A — name-based with class context):
 * - Declarations: match by FQN (file package + enclosing class name)
 * - Implementors: match supertypes by short name + import analysis
 * - Call sites: rename method calls in files that reference the target class
 *
 * Phase B (future): bytecode-guided precision using ASM line numbers.
 */
object PsiRenameMethodRewriter {

    fun rename(
        sourceRoots: List<File>,
        className: String,
        methodName: String,
        newName: String,
        preview: Boolean = false,
    ): RenameMethodResult {
        val sourceFiles = collectSourceFiles(sourceRoots)
        if (sourceFiles.isEmpty()) return RenameMethodResult(emptyList())

        val disposable = Disposer.newDisposable("psi-rename")
        try {
            val configuration = CompilerConfiguration().apply {
                put(
                    CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                    PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false),
                )
                put(CommonConfigurationKeys.MODULE_NAME, "rename-target")
            }
            val environment = KotlinCoreEnvironment.createForProduction(
                disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
            val psiFactory = KtPsiFactory(environment.project)

            val targetSimpleName = className.substringAfterLast(".")
            val targetPackage = className.substringBeforeLast(".", "")

            val changes = mutableListOf<RenameChange>()

            for (file in sourceFiles) {
                val content = file.readText()
                val ktFile = psiFactory.createFile(file.name, content)

                val edits = findEditsInFile(ktFile, className, targetSimpleName, targetPackage, methodName, newName)

                if (edits.isNotEmpty()) {
                    val after = applyEdits(content, edits)
                    changes.add(RenameChange(file.absolutePath, content, after))
                }
            }

            if (!preview) {
                for (change in changes) {
                    File(change.filePath).writeText(change.after)
                }
            }

            return RenameMethodResult(changes)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun findEditsInFile(
        ktFile: KtFile,
        targetFqn: String,
        targetSimpleName: String,
        targetPackage: String,
        methodName: String,
        newName: String,
    ): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()
        val filePackage = ktFile.packageFqName.asString()

        // Find method declarations in target class or implementors
        val classDecls = ktFile.collectDescendantsOfType<KtClass>()
        for (clazz in classDecls) {
            val classFqn = buildFqn(filePackage, clazz)
            val isTarget = matchesFqn(classFqn, targetFqn)
            val isImplementor = !isTarget && implementsTarget(clazz, targetSimpleName, targetFqn, ktFile)

            if (isTarget || isImplementor) {
                // Rename method declarations
                val methods = clazz.declarations
                    .filterIsInstance<KtNamedFunction>()
                    .filter { it.name == methodName }
                for (method in methods) {
                    val nameId = method.nameIdentifier ?: continue
                    edits.add(TextEdit(nameId.textOffset, nameId.textLength, newName))
                }

                // Also check companion object inside this class
                val companions = clazz.companionObjects
                for (companion in companions) {
                    val companionMethods = companion.declarations
                        .filterIsInstance<KtNamedFunction>()
                        .filter { it.name == methodName }
                    for (method in companionMethods) {
                        val nameId = method.nameIdentifier ?: continue
                        edits.add(TextEdit(nameId.textOffset, nameId.textLength, newName))
                    }
                }

                // Rename call sites within the target class (self-calls)
                findCallSiteEdits(clazz, methodName, newName, edits)
            }
        }

        // Find call sites in files that reference the target class
        if (fileReferencesClass(ktFile, targetSimpleName, targetFqn, targetPackage)) {
            // Find call sites outside of target class declarations
            findCallSiteEditsInFile(ktFile, classDecls, targetFqn, targetSimpleName, filePackage, methodName, newName, edits)
        }

        return edits.distinctBy { it.offset }
    }

    private fun findCallSiteEdits(
        scope: KtElement,
        methodName: String,
        newName: String,
        edits: MutableList<TextEdit>,
    ) {
        val callExprs = scope.collectDescendantsOfType<KtCallExpression>()
        for (call in callExprs) {
            val callee = call.calleeExpression
            if (callee is KtNameReferenceExpression && callee.getReferencedName() == methodName) {
                edits.add(TextEdit(callee.textOffset, callee.textLength, newName))
            }
        }
    }

    private fun findCallSiteEditsInFile(
        ktFile: KtFile,
        classDecls: List<KtClass>,
        targetFqn: String,
        targetSimpleName: String,
        filePackage: String,
        methodName: String,
        newName: String,
        edits: MutableList<TextEdit>,
    ) {
        // Find all call expressions in the file that are NOT inside a target/implementor class
        // (those were already handled above)
        val targetClassRanges = classDecls
            .filter { clazz ->
                val fqn = buildFqn(filePackage, clazz)
                matchesFqn(fqn, targetFqn) || implementsTarget(clazz, targetSimpleName, targetFqn, ktFile)
            }
            .map { it.textRange }

        val allCalls = ktFile.collectDescendantsOfType<KtCallExpression>()
        for (call in allCalls) {
            val callee = call.calleeExpression
            if (callee is KtNameReferenceExpression && callee.getReferencedName() == methodName) {
                // Skip if inside a target class (already handled)
                if (targetClassRanges.any { it.contains(callee.textRange) }) continue
                // Only rename dot-qualified calls or calls where we're confident about the target
                val parent = call.parent
                if (parent is KtDotQualifiedExpression) {
                    edits.add(TextEdit(callee.textOffset, callee.textLength, newName))
                }
            }
        }
    }

    private fun buildFqn(filePackage: String, clazz: KtClass): String {
        // Handle nested classes by walking up
        val names = mutableListOf(clazz.name ?: "")
        var parent = clazz.parent
        while (parent != null) {
            if (parent is KtClass) {
                names.add(0, parent.name ?: "")
            }
            parent = parent.parent
        }
        val classPath = names.joinToString(".")
        return if (filePackage.isEmpty()) classPath else "$filePackage.$classPath"
    }

    private fun matchesFqn(classFqn: String, targetFqn: String): Boolean {
        if (classFqn == targetFqn) return true
        // Companion: "com.example.Foo.Companion" matches target "com.example.Foo"
        if (classFqn == "$targetFqn.Companion") return true
        return false
    }

    private fun implementsTarget(
        clazz: KtClass,
        targetSimpleName: String,
        targetFqn: String,
        ktFile: KtFile,
    ): Boolean {
        val supertypes = clazz.superTypeListEntries
        for (entry in supertypes) {
            val typeRef = entry.typeReference?.text?.substringBefore("<") ?: continue
            val simpleName = typeRef.substringAfterLast(".")
            if (simpleName == targetSimpleName) {
                // Verify via imports that this refers to the target
                if (isImportedOrSamePackage(ktFile, targetFqn)) return true
            }
        }
        return false
    }

    private fun fileReferencesClass(
        ktFile: KtFile,
        targetSimpleName: String,
        targetFqn: String,
        targetPackage: String,
    ): Boolean {
        val filePackage = ktFile.packageFqName.asString()
        // Same package — always visible
        if (filePackage == targetPackage) return true
        // Check imports
        return isImportedOrSamePackage(ktFile, targetFqn)
    }

    private fun isImportedOrSamePackage(ktFile: KtFile, targetFqn: String): Boolean {
        val filePackage = ktFile.packageFqName.asString()
        val targetPackage = targetFqn.substringBeforeLast(".", "")
        if (filePackage == targetPackage) return true

        val imports = ktFile.importDirectives
        for (imp in imports) {
            val importedFqn = imp.importedFqName?.asString() ?: continue
            if (importedFqn == targetFqn) return true
            // Star import of the package
            if (imp.isAllUnder && importedFqn == targetPackage) return true
        }
        return false
    }

    private fun applyEdits(content: String, edits: List<TextEdit>): String {
        var result = content
        // Apply in reverse offset order to preserve positions
        for (edit in edits.sortedByDescending { it.offset }) {
            result = result.substring(0, edit.offset) + edit.replacement + result.substring(edit.offset + edit.length)
        }
        return result
    }

    private data class TextEdit(val offset: Int, val length: Int, val replacement: String)
}
