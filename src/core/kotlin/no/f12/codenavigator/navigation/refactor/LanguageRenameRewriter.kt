package no.f12.codenavigator.navigation.refactor

import java.io.File

/**
 * Language-specific rename method logic. Each implementation handles
 * finding edits (declaration + call sites) in files of a specific language.
 */
interface LanguageRenameRewriter {
    /** File extensions this rewriter handles (e.g., "kt", "java") */
    val supportedExtensions: Set<String>

    /**
     * Find text edits needed to rename a method in the given file content.
     *
     * @param content The file content
     * @param fileName The file name (for PSI parsing)
     * @param className FQN of the class owning the method
     * @param methodName Current method name
     * @param newName New method name
     * @param isBytecodeCallSite Whether bytecode analysis identified this file as a call site
     * @param implementorFqns FQNs of classes implementing/extending the target
     * @param filePackage The package declared in this file (if known)
     * @return List of text edits to apply
     */
    fun findEdits(
        content: String,
        fileName: String,
        className: String,
        methodName: String,
        newName: String,
        isBytecodeCallSite: Boolean,
        implementorFqns: Set<String>,
    ): List<TextEdit>
}

data class TextEdit(val offset: Int, val length: Int, val replacement: String)
