package no.f12.codenavigator.navigation.refactor

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

        return withKotlinPsiFactory("rename-property-target") { psiFactory ->
            val targetSimpleName = className.substringAfterLast(".")

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
                    if (!matchesFqn(classFqn, className)) continue

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
                if (fileReferencesClass(ktFile, className)) {
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

            RenamePropertyResult(changes)
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
                    return matchesFqn(fqn, className)
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

}
