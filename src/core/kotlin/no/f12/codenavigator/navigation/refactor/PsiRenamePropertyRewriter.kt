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
 * PSI-based rename property rewriter. Replaces OpenRewrite for property renaming.
 *
 * Handles:
 * - val/var declaration rename in primary constructor
 * - Property access sites (instance.property)
 * - Named arguments at constructor call sites
 * - Named arguments in copy() calls
 */
object PsiRenamePropertyRewriter {

    fun rename(
        sourceRoots: List<File>,
        className: String,
        propertyName: String,
        newName: String,
        preview: Boolean = false,
    ): RenamePropertyResult {
        val sourceFiles = sourceRoots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        if (sourceFiles.isEmpty()) return RenamePropertyResult(emptyList())

        val disposable = Disposer.newDisposable("psi-rename-property")
        try {
            val configuration = CompilerConfiguration().apply {
                put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                    PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false))
                put(CommonConfigurationKeys.MODULE_NAME, "rename-property-target")
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
                val filePackage = ktFile.packageFqName.asString()

                val edits = mutableListOf<TextEdit>()

                val classDecls = ktFile.collectDescendantsOfType<KtClass>()

                // Process target class: rename declaration
                for (clazz in classDecls) {
                    val classFqn = buildClassFqn(filePackage, clazz)
                    if (!matchesClass(classFqn, className)) continue

                    // Check if property exists as a body declaration
                    val hasBodyProperty = clazz.declarations.any { it is KtProperty && it.name == propertyName }

                    // Rename val/var in primary constructor
                    val constructor = clazz.primaryConstructor
                    if (constructor != null) {
                        for (param in constructor.valueParameters) {
                            if (param.name == propertyName) {
                                // Rename if it's a val/var property, OR if it's a plain parameter
                                // that initializes a body property with the same name
                                if (param.hasValOrVar() || hasBodyProperty) {
                                    val nameId = param.nameIdentifier ?: continue
                                    edits.add(TextEdit(nameId.textOffset, nameId.textLength, newName))
                                }
                            }
                        }
                    }

                    // Rename val/var declared in class body
                    for (decl in clazz.declarations) {
                        if (decl is KtProperty && decl.name == propertyName) {
                            val nameId = decl.nameIdentifier ?: continue
                            edits.add(TextEdit(nameId.textOffset, nameId.textLength, newName))
                            // Also rename references to the constructor param in the initializer
                            val initializer = decl.initializer
                            if (initializer is KtNameReferenceExpression && initializer.getReferencedName() == propertyName) {
                                edits.add(TextEdit(initializer.textOffset, initializer.textLength, newName))
                            }
                        }
                    }
                }

                // Rename property access sites and named args across the file
                if (fileReferencesClass(ktFile, targetSimpleName, targetPackage, filePackage)) {
                    // Dot-qualified property access: instance.propertyName
                    val dotExprs = ktFile.collectDescendantsOfType<KtDotQualifiedExpression>()
                    for (dot in dotExprs) {
                        val selector = dot.selectorExpression
                        if (selector is KtNameReferenceExpression && selector.getReferencedName() == propertyName) {
                            // Heuristic: rename if receiver could be the target type
                            // We check if receiver references target simple name or is a variable likely of that type
                            if (couldBeTargetAccess(dot, targetSimpleName, classDecls, filePackage, className)) {
                                edits.add(TextEdit(selector.textOffset, selector.textLength, newName))
                            }
                        }
                    }

                    // Named arguments at constructor call sites: TargetClass(propertyName = ...)
                    val callExprs = ktFile.collectDescendantsOfType<KtCallExpression>()
                    for (call in callExprs) {
                        val callee = call.calleeExpression as? KtNameReferenceExpression ?: continue
                        val calledName = callee.getReferencedName()

                        if (calledName == targetSimpleName) {
                            // Constructor call
                            renameNamedArgInCall(call, propertyName, newName, edits)
                        } else if (calledName == "copy") {
                            // copy() call — check if receiver is the target type (heuristic)
                            val parent = call.parent
                            if (parent is KtDotQualifiedExpression) {
                                renameNamedArgInCall(call, propertyName, newName, edits)
                            }
                        }
                    }
                }

                if (edits.isNotEmpty()) {
                    val uniqueEdits = edits.distinctBy { it.offset }
                    val after = applyEdits(content, uniqueEdits)
                    if (after != content) {
                        changes.add(RenameChange(file.absolutePath, content, after))
                    }
                }
            }

            if (!preview) {
                for (change in changes) {
                    File(change.filePath).writeText(change.after)
                }
            }

            return RenamePropertyResult(changes)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun couldBeTargetAccess(
        dot: KtDotQualifiedExpression,
        targetSimpleName: String,
        classDecls: List<KtClass>,
        filePackage: String,
        className: String,
    ): Boolean {
        // If the receiver is a simple name that matches the target class, skip (static access)
        // For property access we assume any dot.property matching the name is valid
        // This is a heuristic — without type resolution we can't be 100% sure
        val receiver = dot.receiverExpression

        // If receiver is a constructor call to the target class, definitely yes
        if (receiver is KtCallExpression) {
            val callee = receiver.calleeExpression as? KtNameReferenceExpression
            if (callee?.getReferencedName() == targetSimpleName) return true
        }

        // If receiver is a simple name reference (variable), assume it could be the target type
        // unless it's clearly a class name (starts with uppercase and matches a class in this file)
        if (receiver is KtNameReferenceExpression) {
            val name = receiver.getReferencedName()
            // If the name matches a class in this file that ISN'T the target, skip
            for (clazz in classDecls) {
                if (clazz.name == name) {
                    val fqn = buildClassFqn(filePackage, clazz)
                    return matchesClass(fqn, className)
                }
            }
            // Variable reference — assume yes (heuristic)
            return true
        }

        // Chained dot expressions — assume yes
        return true
    }

    private fun renameNamedArgInCall(
        call: KtCallExpression,
        propertyName: String,
        newName: String,
        edits: MutableList<TextEdit>,
    ) {
        for (arg in call.valueArguments) {
            val argName = arg.getArgumentName()
            if (argName != null && argName.text == propertyName) {
                val nameRef = argName.referenceExpression ?: continue
                edits.add(TextEdit(nameRef.textOffset, nameRef.textLength, newName))
            }
        }
    }

    private fun fileReferencesClass(
        ktFile: KtFile,
        targetSimpleName: String,
        targetPackage: String,
        filePackage: String,
    ): Boolean {
        if (filePackage == targetPackage) return true
        val imports = ktFile.importDirectives
        val targetFqn = if (targetPackage.isEmpty()) targetSimpleName else "$targetPackage.$targetSimpleName"
        for (imp in imports) {
            val importedFqn = imp.importedFqName?.asString() ?: continue
            if (importedFqn == targetFqn) return true
            if (imp.isAllUnder && importedFqn == targetPackage) return true
        }
        return false
    }

    private fun buildClassFqn(filePackage: String, clazz: KtClass): String {
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

    private fun matchesClass(classFqn: String?, targetClassName: String): Boolean {
        if (classFqn == null) return false
        if (classFqn == targetClassName) return true
        if (classFqn == "$targetClassName.Companion") return true
        return false
    }

    private fun applyEdits(content: String, edits: List<TextEdit>): String {
        var result = content
        for (edit in edits.sortedByDescending { it.offset }) {
            result = result.substring(0, edit.offset) + edit.replacement + result.substring(edit.offset + edit.length)
        }
        return result
    }
}
