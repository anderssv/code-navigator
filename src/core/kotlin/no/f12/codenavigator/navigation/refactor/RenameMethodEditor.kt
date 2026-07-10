package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import java.io.File

/**
 * Multi-language rename method rewriter. Dispatches to language-specific
 * rewriters (Kotlin, Java) based on file extension.
 *
 * Strategy:
 * - Declarations: match by FQN (package + class name) + implementor FQNs from bytecode
 * - Call sites: rename in files identified by bytecode as calling the target method,
 *   plus files reachable via import/package analysis (Phase A fallback)
 */
object RenameMethodEditor {

    private val rewriters: List<LanguageRenameRewriter> = listOf(
        KotlinRenameMethodRewriter(),
        JavaRenameMethodRewriter(),
    )

    private val supportedExtensions: Set<String> = rewriters.flatMap { it.supportedExtensions }.toSet()

    /**
     * @param callSiteFiles Relative source paths (from bytecode) that call the target method.
     *   When provided, these files are also scanned for call sites regardless of imports.
     * @param implementorFqns FQNs of classes that implement/extend the target (from bytecode).
     *   When provided, declarations in these classes are also renamed.
     */
    fun rename(
        sourceRoots: List<File>,
        className: String,
        methodName: String,
        newName: String,
        preview: Boolean = false,
        callSiteFiles: Set<String> = emptySet(),
        implementorFqns: Set<String> = emptySet(),
    ): RenameMethodResult {
        val sourceFiles = collectAllSourceFiles(sourceRoots)
        if (sourceFiles.isEmpty()) return RenameMethodResult(emptyList())

        try {
            val changes = mutableListOf<RenameChange>()

            for (file in sourceFiles) {
                val rewriter = rewriterFor(file) ?: continue
                val content = file.readText()

                // Check if this file is bytecode-identified as a call site
                val relativePath = sourceRoots.firstNotNullOfOrNull { root ->
                    val rel = file.toRelativeString(root)
                    if (!rel.startsWith("..")) rel else null
                } ?: file.name
                val isBytecodeCallSite = callSiteFiles.any { relativePath.endsWith(it) || it.endsWith(relativePath) }

                val edits = rewriter.findEdits(
                    content = content,
                    fileName = file.name,
                    className = className,
                    methodName = methodName,
                    newName = newName,
                    isBytecodeCallSite = isBytecodeCallSite,
                    implementorFqns = implementorFqns,
                )

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
            rewriters.forEach { rewriter ->
                when (rewriter) {
                    is KotlinRenameMethodRewriter -> rewriter.dispose()
                    is JavaRenameMethodRewriter -> rewriter.dispose()
                }
            }
        }
    }

    private fun rewriterFor(file: File): LanguageRenameRewriter? =
        rewriters.firstOrNull { file.extension in it.supportedExtensions }

    private fun collectAllSourceFiles(sourceRoots: List<File>): List<File> =
        sourceRoots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension in supportedExtensions }
                .toList()
        }
}
