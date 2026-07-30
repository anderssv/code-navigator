package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File

/**
 * Result of locating a class (and optionally a method) in source before any rewriting takes place.
 *
 * Separates finding from doing: callers pass [DeclarationLocation] into a rewriter rather than
 * each rewriter duplicating its own source-file scan.
 *
 * @param declarationFile the file that contains the class (or class+method) declaration, or null if not found.
 * @param callSiteFiles   all files that reference the class (same-package or via import), including the
 *                        declaration file — the full set a call-site rewriter must consider.
 * @param overrideFamilyFqns the full set of FQNs (bytecode-derived) that must be renamed together when
 *                        renaming a method — includes sibling implementors and interface parents.
 *                        Empty when the caller has no bytecode available or doesn't need it.
 */
data class DeclarationLocation(
    val declarationFile: File?,
    val callSiteFiles: Set<File>,
    val overrideFamilyFqns: Set<String> = emptySet(),
) {
    val found: Boolean get() = declarationFile != null
}

/**
 * Locates a class declaration across a set of source roots by building the full FQN of every
 * [KtClass] in each file and matching against [className] via [matchesFqn] / [buildClassFqn].
 *
 * This replaces the repeated per-rewriter scan loops in [PsiRenamePropertyRewriter],
 * [PsiRenameParamRewriter], [ChangeSignatureRewriter], and [SafeDeleteRewriter].
 */
object DeclarationFinder {

    /**
     * Locate the source file that declares [className] and collect all files that reference it.
     * [overrideFamilyFqns] should be pre-computed from [RenameLocationFinder] when bytecode is
     * available; defaults to empty (no override-family stitching).
     */
    fun locate(
        sourceRoots: List<File>,
        className: String,
        overrideFamilyFqns: Set<String> = emptySet(),
    ): DeclarationLocation {
        val sourceFiles = sourceRoots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        if (sourceFiles.isEmpty()) return DeclarationLocation(null, emptySet(), overrideFamilyFqns)

        var declarationFile: File? = null
        val callSiteFiles = mutableSetOf<File>()

        withKotlinPsiFactory("declaration-finder") { psiFactory ->
            for (file in sourceFiles) {
                val content = file.readText()
                val ktFile = psiFactory.createFile(file.name, content)
                val filePackage = ktFile.packageFqName.asString()

                val hasDeclaration = ktFile.collectDescendantsOfType<KtClass>()
                    .any { matchesFqn(buildClassFqn(filePackage, it), className) }

                if (hasDeclaration) {
                    declarationFile = file
                    callSiteFiles.add(file)
                } else if (fileReferencesClass(ktFile, className)) {
                    callSiteFiles.add(file)
                }
            }
        }

        return DeclarationLocation(declarationFile, callSiteFiles, overrideFamilyFqns)
    }
}
