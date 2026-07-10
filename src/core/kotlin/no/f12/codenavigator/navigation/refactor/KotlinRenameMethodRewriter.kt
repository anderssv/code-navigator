package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Kotlin-specific rename method rewriter using Kotlin PSI.
 * Handles .kt files: finds declarations and call sites using KtPsiFactory.
 */
class KotlinRenameMethodRewriter : LanguageRenameRewriter {

    override val supportedExtensions = setOf("kt")

    private var cachedEnvironment: Pair<Any, KtPsiFactory>? = null

    override fun findEdits(
        content: String,
        fileName: String,
        className: String,
        methodName: String,
        newName: String,
        isBytecodeCallSite: Boolean,
        implementorFqns: Set<String>,
    ): List<TextEdit> {
        val (_, psiFactory) = getOrCreateEnvironment()
        val ktFile = psiFactory.createFile(fileName, content)

        val targetSimpleName = className.substringAfterLast(".")

        return findEditsInFile(
            ktFile, className, targetSimpleName,
            methodName, newName, isBytecodeCallSite, implementorFqns,
        )
    }

    private fun getOrCreateEnvironment(): Pair<Any, KtPsiFactory> {
        cachedEnvironment?.let { return it }
        val (disposable, environment) = createDisposableKotlinEnvironment("kotlin-rename")
        val psiFactory = KtPsiFactory(environment.project)
        val pair = Pair(disposable as Any, psiFactory)
        cachedEnvironment = pair
        return pair
    }

    fun dispose() {
        cachedEnvironment?.let { (disposable, _) ->
            Disposer.dispose(disposable as org.jetbrains.kotlin.com.intellij.openapi.Disposable)
        }
        cachedEnvironment = null
    }

    private fun findEditsInFile(
        ktFile: KtFile,
        targetFqn: String,
        targetSimpleName: String,
        methodName: String,
        newName: String,
        isBytecodeCallSite: Boolean,
        implementorFqns: Set<String>,
    ): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()
        val filePackage = ktFile.packageFqName.asString()

        // Find method declarations in target class or implementors
        val classDecls = ktFile.collectDescendantsOfType<KtClass>()
        for (clazz in classDecls) {
            val classFqn = buildClassFqn(filePackage, clazz)
            val isTarget = matchesFqn(classFqn, targetFqn)
            val isBytecodeImplementor = implementorFqns.contains(classFqn)
            val isImplementor = !isTarget && (isBytecodeImplementor || implementsTarget(clazz, targetSimpleName, targetFqn, ktFile))

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

        // Find call sites in files that reference the target class OR are bytecode-identified
        if (isBytecodeCallSite || fileReferencesClass(ktFile, targetFqn)) {
            findCallSiteEditsInFile(ktFile, classDecls, targetFqn, targetSimpleName, filePackage, methodName, newName, edits, implementorFqns)
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
        implementorFqns: Set<String> = emptySet(),
    ) {
        val targetClassRanges = classDecls
            .filter { clazz ->
                val fqn = buildClassFqn(filePackage, clazz)
                matchesFqn(fqn, targetFqn) || implementorFqns.contains(fqn) || implementsTarget(clazz, targetSimpleName, targetFqn, ktFile)
            }
            .map { it.textRange }

        val allCalls = ktFile.collectDescendantsOfType<KtCallExpression>()
        for (call in allCalls) {
            val callee = call.calleeExpression
            if (callee is KtNameReferenceExpression && callee.getReferencedName() == methodName) {
                if (targetClassRanges.any { it.contains(callee.textRange) }) continue
                val parent = call.parent
                if (parent is KtDotQualifiedExpression) {
                    edits.add(TextEdit(callee.textOffset, callee.textLength, newName))
                }
            }
        }
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
                if (fileReferencesClass(ktFile, targetFqn)) return true
            }
        }
        return false
    }
}
