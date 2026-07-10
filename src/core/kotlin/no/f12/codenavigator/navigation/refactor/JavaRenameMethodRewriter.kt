package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.*
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil

/**
 * Java-specific rename method rewriter using IntelliJ Java PSI
 * (bundled with kotlin-compiler-embeddable).
 * Handles .java files: finds declarations and call sites.
 */
class JavaRenameMethodRewriter : LanguageRenameRewriter {

    override val supportedExtensions = setOf("java")

    private var cachedEnvironment: Pair<Any, PsiFileFactory>? = null

    override fun findEdits(
        content: String,
        fileName: String,
        className: String,
        methodName: String,
        newName: String,
        isBytecodeCallSite: Boolean,
        implementorFqns: Set<String>,
    ): List<TextEdit> {
        val (_, fileFactory) = getOrCreateEnvironment()
        val psiFile = fileFactory.createFileFromText(
            fileName,
            org.jetbrains.kotlin.com.intellij.lang.java.JavaLanguage.INSTANCE,
            content,
        )

        val targetSimpleName = className.substringAfterLast(".")
        val targetPackage = className.substringBeforeLast(".", "")

        return findEditsInFile(
            psiFile, className, targetSimpleName, targetPackage,
            methodName, newName, isBytecodeCallSite, implementorFqns,
        )
    }

    private fun getOrCreateEnvironment(): Pair<Any, PsiFileFactory> {
        cachedEnvironment?.let { return it }
        val (disposable, environment) = createDisposableKotlinEnvironment("java-rename")
        val fileFactory = PsiFileFactory.getInstance(environment.project)
        val pair = Pair(disposable as Any, fileFactory)
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
        psiFile: PsiFile,
        targetFqn: String,
        targetSimpleName: String,
        targetPackage: String,
        methodName: String,
        newName: String,
        isBytecodeCallSite: Boolean,
        implementorFqns: Set<String>,
    ): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()

        val javaFile = psiFile as? PsiJavaFile ?: return emptyList()
        val filePackage = javaFile.packageName

        // Find class declarations
        val classes = PsiTreeUtil.findChildrenOfType(javaFile, PsiClass::class.java)
        for (clazz in classes) {
            val classFqn = clazz.qualifiedName ?: buildFqn(filePackage, clazz)
            val isTarget = classFqn == targetFqn
            val isBytecodeImplementor = implementorFqns.contains(classFqn)
            val isImplementor = !isTarget && (isBytecodeImplementor || implementsTarget(clazz, targetSimpleName, targetFqn, javaFile))

            if (isTarget || isImplementor) {
                // Rename method declarations
                for (method in clazz.methods) {
                    if (method.name == methodName) {
                        val nameId = method.nameIdentifier ?: continue
                        edits.add(TextEdit(nameId.textOffset, nameId.textLength, newName))
                    }
                }

                // Rename call sites within this class (self-calls)
                findCallSiteEdits(clazz, methodName, newName, edits)
            }
        }

        // Find call sites in files that reference the target class OR are bytecode-identified
        if (isBytecodeCallSite || fileReferencesClass(javaFile, targetSimpleName, targetFqn, targetPackage)) {
            findCallSiteEditsOutsideTargetClasses(javaFile, classes, targetFqn, filePackage, methodName, newName, edits, implementorFqns)
        }

        return edits.distinctBy { it.offset }
    }

    private fun findCallSiteEdits(
        scope: PsiElement,
        methodName: String,
        newName: String,
        edits: MutableList<TextEdit>,
    ) {
        val methodCalls = PsiTreeUtil.findChildrenOfType(scope, PsiMethodCallExpression::class.java)
        for (call in methodCalls) {
            val ref = call.methodExpression.referenceNameElement
            if (ref != null && ref.text == methodName) {
                edits.add(TextEdit(ref.textOffset, ref.textLength, newName))
            }
        }
    }

    private fun findCallSiteEditsOutsideTargetClasses(
        javaFile: PsiJavaFile,
        classes: Collection<PsiClass>,
        targetFqn: String,
        filePackage: String,
        methodName: String,
        newName: String,
        edits: MutableList<TextEdit>,
        implementorFqns: Set<String>,
    ) {
        val targetClassRanges = classes
            .filter { clazz ->
                val fqn = clazz.qualifiedName ?: buildFqn(filePackage, clazz)
                fqn == targetFqn || implementorFqns.contains(fqn)
            }
            .map { it.textRange }

        val allCalls = PsiTreeUtil.findChildrenOfType(javaFile, PsiMethodCallExpression::class.java)
        for (call in allCalls) {
            val ref = call.methodExpression.referenceNameElement
            if (ref != null && ref.text == methodName) {
                if (targetClassRanges.any { it.contains(ref.textRange) }) continue
                edits.add(TextEdit(ref.textOffset, ref.textLength, newName))
            }
        }
    }

    private fun buildFqn(filePackage: String, clazz: PsiClass): String {
        val name = clazz.name ?: ""
        return if (filePackage.isEmpty()) name else "$filePackage.$name"
    }

    private fun implementsTarget(
        clazz: PsiClass,
        targetSimpleName: String,
        targetFqn: String,
        javaFile: PsiJavaFile,
    ): Boolean {
        val extendsList = clazz.extendsList?.referenceElements ?: emptyArray()
        val implementsList = clazz.implementsList?.referenceElements ?: emptyArray()
        for (ref in extendsList + implementsList) {
            val simpleName = ref.referenceName
            if (simpleName == targetSimpleName) {
                if (isImportedOrSamePackage(javaFile, targetFqn)) return true
            }
        }
        return false
    }

    private fun fileReferencesClass(
        javaFile: PsiJavaFile,
        targetSimpleName: String,
        targetFqn: String,
        targetPackage: String,
    ): Boolean {
        val filePackage = javaFile.packageName
        if (filePackage == targetPackage) return true
        return isImportedOrSamePackage(javaFile, targetFqn)
    }

    private fun isImportedOrSamePackage(javaFile: PsiJavaFile, targetFqn: String): Boolean {
        val filePackage = javaFile.packageName
        val targetPackage = targetFqn.substringBeforeLast(".", "")
        if (filePackage == targetPackage) return true

        val importList = javaFile.importList ?: return false
        for (imp in importList.importStatements) {
            val importedFqn = imp.qualifiedName ?: continue
            if (importedFqn == targetFqn) return true
            // Star import
            if (imp.isOnDemand && importedFqn == targetPackage) return true
        }
        return false
    }
}
