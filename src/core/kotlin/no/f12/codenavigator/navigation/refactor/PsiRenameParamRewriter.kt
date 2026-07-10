package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.io.File

/**
 * PSI-based rename parameter rewriter. Replaces OpenRewrite for parameter renaming.
 *
 * Handles:
 * - Parameter declaration rename in target method
 * - Identifier references within the method body
 * - Named arguments at call sites (same-file and cross-file)
 * - Cascade detection (param forwarded to same-named param)
 * - Companion object methods (className refers to outer class)
 */
object PsiRenameParamRewriter {

    private val CONSTRUCTOR_METHOD_NAMES = setOf("<init>", "<constructor>")

    fun rename(
        sourceRoots: List<File>,
        className: String,
        methodName: String,
        paramName: String,
        newName: String,
        preview: Boolean = false,
    ): RenameResult {
        val sourceFiles = sourceRoots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        if (sourceFiles.isEmpty()) return RenameResult(emptyList())

        val warnings = mutableListOf<String>()

        if (isConstructorMethod(methodName)) {
            val simpleClassName = className.substringAfterLast(".")
            val isValVar = sourceFiles.any { file ->
                isValVarConstructorParam(file, simpleClassName, paramName)
            }
            if (isValVar) {
                warnings.add(
                    "WARNING: Parameter '$paramName' is a val/var constructor property on $simpleClassName. " +
                        "This refactoring only renames the constructor parameter and named arguments at " +
                        "constructor call sites. For a full property rename (including all property access " +
                        "sites, copy() calls, and constructor named arguments), use rename-property instead.",
                )
            }
        }

        return withKotlinPsiFactory("rename-param-target") { psiFactory ->

            val targetSimpleName = className.substringAfterLast(".")
            val targetPackage = className.substringBeforeLast(".", "")
            val isConstructor = isConstructorMethod(methodName)

            val changes = mutableListOf<RenameChange>()
            val cascadeCandidates = mutableSetOf<CascadeCandidate>()

            for (file in sourceFiles) {
                val content = file.readText()
                val ktFile = psiFactory.createFile(file.name, content)
                val filePackage = ktFile.packageFqName.asString()

                val edits = mutableListOf<TextEdit>()

                // Find target class declarations
                val classDecls = ktFile.collectDescendantsOfType<KtClass>()
                for (clazz in classDecls) {
                    val classFqn = buildClassFqn(filePackage, clazz)
                    if (!matchesClassOrCompanion(classFqn, className)) continue

                    // Find the target method
                    val methods = if (isConstructor) {
                        // For constructors, look at primary constructor parameters
                        val primaryConstructor = clazz.primaryConstructor
                        if (primaryConstructor != null) {
                            renameConstructorParam(primaryConstructor, paramName, newName, edits)
                        }
                        emptyList()
                    } else {
                        clazz.declarations.filterIsInstance<KtNamedFunction>().filter { it.name == methodName }
                    }

                    for (method in methods) {
                        renameParamInMethod(method, paramName, newName, edits, cascadeCandidates)
                    }

                    // Also check companion object
                    for (companion in clazz.companionObjects) {
                        val companionMethods = companion.declarations
                            .filterIsInstance<KtNamedFunction>()
                            .filter { it.name == methodName }
                        for (method in companionMethods) {
                            renameParamInMethod(method, paramName, newName, edits, cascadeCandidates)
                        }
                    }

                    // Rename named args in ALL calls to the target method within this class
                    // (including calls from other methods in the same class)
                    if (!isConstructor) {
                        val allCallsInClass = clazz.collectDescendantsOfType<KtCallExpression>()
                        for (call in allCallsInClass) {
                            val callee = call.calleeExpression as? KtNameReferenceExpression ?: continue
                            if (callee.getReferencedName() != methodName) continue
                            renameNamedArgInCall(call, paramName, newName, edits)
                        }
                    }
                }

                // Find named arguments at call sites
                if (fileReferencesClass(ktFile, targetSimpleName, targetPackage)) {
                    findNamedArgEdits(ktFile, classDecls, filePackage, className, targetSimpleName, methodName, paramName, newName, edits, isConstructor)
                }

                if (edits.isNotEmpty()) {
                    val uniqueEdits = edits.distinctBy { it.offset }
                    val after = applyEdits(content, uniqueEdits)
                    changes.add(RenameChange(file.absolutePath, content, after))
                }
            }

            if (!preview) {
                for (change in changes) {
                    File(change.filePath).writeText(change.after)
                }
            }

            RenameResult(changes, cascadeCandidates.toList(), warnings)
        }
    }

    private fun renameParamInMethod(
        method: KtNamedFunction,
        paramName: String,
        newName: String,
        edits: MutableList<TextEdit>,
        cascadeCandidates: MutableSet<CascadeCandidate>,
    ) {
        // Rename the parameter declaration
        val param = method.valueParameters.firstOrNull { it.name == paramName } ?: return
        val paramNameId = param.nameIdentifier ?: return
        edits.add(TextEdit(paramNameId.textOffset, paramNameId.textLength, newName))

        // Rename identifier references within the method body
        val body = method.bodyExpression ?: method.bodyBlockExpression ?: return
        renameIdentifiersInScope(body, paramName, newName, edits, cascadeCandidates, method)
    }

    private fun renameConstructorParam(
        constructor: KtPrimaryConstructor,
        paramName: String,
        newName: String,
        edits: MutableList<TextEdit>,
    ) {
        val param = constructor.valueParameters.firstOrNull { it.name == paramName } ?: return
        val paramNameId = param.nameIdentifier ?: return
        edits.add(TextEdit(paramNameId.textOffset, paramNameId.textLength, newName))
    }

    private fun renameIdentifiersInScope(
        scope: KtElement,
        paramName: String,
        newName: String,
        edits: MutableList<TextEdit>,
        cascadeCandidates: MutableSet<CascadeCandidate>,
        method: KtNamedFunction,
    ) {
        // Rename simple name references to the param
        val refs = scope.collectDescendantsOfType<KtNameReferenceExpression>()
        for (ref in refs) {
            if (ref.getReferencedName() != paramName) continue

            // Skip if this is a named argument key (inside KtValueArgumentName)
            val parent = ref.parent
            if (parent is KtValueArgumentName) continue

            edits.add(TextEdit(ref.textOffset, ref.textLength, newName))
        }

        // Detect cascade candidates: calls where renamed param is forwarded
        val calls = scope.collectDescendantsOfType<KtCallExpression>()
        for (call in calls) {
            val callee = call.calleeExpression as? KtNameReferenceExpression ?: continue
            val calledMethodName = callee.getReferencedName()
            val args = call.valueArguments

            for ((index, arg) in args.withIndex()) {
                val argExpr = arg.getArgumentExpression()
                if (argExpr is KtNameReferenceExpression &&
                    (argExpr.getReferencedName() == paramName || argExpr.getReferencedName() == newName)) {
                    // Check if the called method has a same-named param at this position
                    // We can't easily know param names without type resolution,
                    // but we can detect named-arg forwarding: foo(name = name)
                    val argName = arg.getArgumentName()?.text
                    if (argName == paramName) {
                        // Find declaring class for cascade
                        val dotQualified = call.parent as? KtDotQualifiedExpression
                        val receiverType = if (dotQualified != null) "unknown" else {
                            // Self call - same class
                            val containingClass = method.getParentOfType<KtClass>(true)
                            val pkg = (containingClass?.containingKtFile?.packageFqName?.asString() ?: "")
                            val clsName = containingClass?.name ?: ""
                            if (pkg.isEmpty()) clsName else "$pkg.$clsName"
                        }
                        cascadeCandidates.add(CascadeCandidate(receiverType, calledMethodName, paramName))
                    } else if (argName == null) {
                        // Positional arg — would need param name resolution from declaration
                        // For cascade detection, check if called method is in same class and has matching param name
                        val containingClass = method.getParentOfType<KtClass>(true)
                        if (containingClass != null) {
                            val calledMethod = containingClass.declarations
                                .filterIsInstance<KtNamedFunction>()
                                .firstOrNull { it.name == calledMethodName }
                            if (calledMethod != null && index < calledMethod.valueParameters.size) {
                                val calledParamName = calledMethod.valueParameters[index].name
                                if (calledParamName == paramName) {
                                    val pkg = containingClass.containingKtFile?.packageFqName?.asString() ?: ""
                                    val clsName = containingClass.name ?: ""
                                    val fqn = if (pkg.isEmpty()) clsName else "$pkg.$clsName"
                                    cascadeCandidates.add(CascadeCandidate(fqn, calledMethodName, paramName))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findNamedArgEdits(
        ktFile: KtFile,
        classDecls: List<KtClass>,
        filePackage: String,
        targetClassName: String,
        targetSimpleName: String,
        methodName: String,
        paramName: String,
        newName: String,
        edits: MutableList<TextEdit>,
        isConstructor: Boolean,
    ) {
        if (isConstructor) {
            // For constructor calls, find NewClass-style calls (constructor invocations)
            val callExprs = ktFile.collectDescendantsOfType<KtCallExpression>()
            for (call in callExprs) {
                val callee = call.calleeExpression as? KtNameReferenceExpression ?: continue
                if (callee.getReferencedName() != targetSimpleName) continue

                // Skip if this is inside the target class itself (already handled)
                if (isInsideTargetClass(call, classDecls, filePackage, targetClassName)) continue

                renameNamedArgInCall(call, paramName, newName, edits)
            }
        } else {
            // For method calls, find dot-qualified calls to the method
            val callExprs = ktFile.collectDescendantsOfType<KtCallExpression>()
            for (call in callExprs) {
                val callee = call.calleeExpression as? KtNameReferenceExpression ?: continue
                if (callee.getReferencedName() != methodName) continue

                // Skip if inside the target class (already handled by renameIdentifiersInScope)
                if (isInsideTargetClass(call, classDecls, filePackage, targetClassName)) continue

                // Must be a dot-qualified call on an instance of the target class (heuristic)
                val parent = call.parent
                if (parent is KtDotQualifiedExpression) {
                    renameNamedArgInCall(call, paramName, newName, edits)
                }
            }
        }
    }

    private fun renameNamedArgInCall(
        call: KtCallExpression,
        paramName: String,
        newName: String,
        edits: MutableList<TextEdit>,
    ) {
        for (arg in call.valueArguments) {
            val argName = arg.getArgumentName()
            if (argName != null && argName.text == paramName) {
                val nameRef = argName.referenceExpression ?: continue
                edits.add(TextEdit(nameRef.textOffset, nameRef.textLength, newName))
            }
        }
    }

    private fun isInsideTargetClass(
        element: KtElement,
        classDecls: List<KtClass>,
        filePackage: String,
        targetClassName: String,
    ): Boolean {
        for (clazz in classDecls) {
            val fqn = buildClassFqn(filePackage, clazz)
            if (matchesClassOrCompanion(fqn, targetClassName) && clazz.textRange.contains(element.textRange)) {
                return true
            }
        }
        return false
    }

    private fun fileReferencesClass(
        ktFile: KtFile,
        targetSimpleName: String,
        targetPackage: String,
    ): Boolean {
        val filePackage = ktFile.packageFqName.asString()
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

    private fun matchesClassOrCompanion(classFqn: String?, targetClassName: String): Boolean {
        if (classFqn == null) return false
        if (classFqn == targetClassName) return true
        if (classFqn == "$targetClassName.Companion") return true
        return false
    }

    private fun isConstructorMethod(methodName: String): Boolean =
        methodName in CONSTRUCTOR_METHOD_NAMES

    private fun isValVarConstructorParam(file: File, simpleClassName: String, paramName: String): Boolean {
        val content = file.readText()
        val classPattern = Regex("""(?:data\s+)?class\s+$simpleClassName\s*\([^)]*\b(val|var)\s+$paramName\s*:""")
        return classPattern.containsMatchIn(content)
    }

}
