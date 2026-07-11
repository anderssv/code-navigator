package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * PSI-based replacement for OpenRewrite's `ChangeType`: retargets every reference to a fully-qualified
 * type from [oldFqcn] to [newFqcn] across a set of source files. Covers both dimensions a move can carry:
 *
 *  - **package change** — rewrites the fully-qualified forms: import directives
 *    (`import old.pkg.Foo` / `... as Bar`, alias preserved) and fully-qualified references in code
 *    (`val x: old.pkg.Foo`, `old.pkg.Foo()`), matched on exact text so a longer path that merely starts
 *    with the FQN is never touched.
 *  - **simple-name change (rename)** — when the simple name differs, also renames the class/interface/object
 *    *declaration* in its own file and every unqualified simple-name reference to it, but only in files that
 *    actually reference the type (same package or importing it, via [fileReferencesClass]) so an unrelated
 *    same-named type elsewhere is left alone. Trailing segments of already-retargeted fully-qualified
 *    references are dropped by the overlap dedupe so the two passes never collide.
 *
 * It deliberately does *not* touch the declaring file's package declaration or add imports for now-non-local
 * siblings — those are handled by the caller's textual passes, unchanged. Unlike OpenRewrite's
 * `KotlinIsoVisitor`, plain PSI traversal reaches references at any lambda-nesting depth.
 */
object KotlinTypeReferenceRewriter {

    /** Returns path -> new content for every file whose references changed. Files with no reference to [oldFqcn] are omitted. */
    fun retargetAcrossSources(
        sources: List<SourceFileContent>,
        oldFqcn: String,
        newFqcn: String,
    ): Map<String, String> {
        val oldPackage = oldFqcn.substringBeforeLast('.', "")
        val oldSimple = oldFqcn.substringAfterLast('.')
        val newSimple = newFqcn.substringAfterLast('.')

        // Cheap pre-filter: a file can only reference the type if its old package (or the simple name)
        // appears in the text — avoids parsing files that obviously can't match.
        val candidates = sources.filter { it.content.contains(oldPackage.ifEmpty { oldFqcn }) || it.content.contains(oldSimple) }
        if (candidates.isEmpty()) return emptyMap()

        val changed = mutableMapOf<String, String>()
        withKotlinPsiFactory("move-class-retarget") { psiFactory ->
            for (source in candidates) {
                val ktFile = psiFactory.createFile(fileNameOf(source.path), source.content)
                val edits = collectEdits(ktFile, oldFqcn, newFqcn, oldPackage, oldSimple, newSimple)
                if (edits.isEmpty()) continue
                val updated = applyEdits(source.content, edits)
                if (updated != source.content) changed[source.path] = updated
            }
        }
        return changed
    }

    private fun collectEdits(
        ktFile: KtFile,
        oldFqcn: String,
        newFqcn: String,
        oldPackage: String,
        oldSimple: String,
        newSimple: String,
    ): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()

        // --- Fully-qualified forms (the package-change dimension) ---
        for (directive in ktFile.collectDescendantsOfType<KtImportDirective>()) {
            if (directive.importedFqName?.asString() != oldFqcn) continue
            val ref = directive.importedReference ?: continue
            edits.add(TextEdit(ref.textOffset, ref.textLength, newFqcn))
        }
        for (userType in ktFile.collectDescendantsOfType<KtUserType>()) {
            if (userType.text == oldFqcn) edits.add(TextEdit(userType.textOffset, userType.textLength, newFqcn))
        }
        for (expr in ktFile.collectDescendantsOfType<KtDotQualifiedExpression>()) {
            if (expr.text == oldFqcn) edits.add(TextEdit(expr.textOffset, expr.textLength, newFqcn))
        }

        // --- Simple-name rename (only when the simple name actually changes) ---
        if (oldSimple != newSimple && fileReferencesClass(ktFile, oldFqcn)) {
            // The declaration itself, in its own file.
            if (ktFile.packageFqName.asString() == oldPackage) {
                for (decl in ktFile.collectDescendantsOfType<KtClassOrObject>()) {
                    if (decl.name != oldSimple) continue
                    val nameId = decl.nameIdentifier ?: continue
                    edits.add(TextEdit(nameId.textOffset, nameId.textLength, newSimple))
                }
            }
            // Unqualified references: type positions and expression positions. Contained references
            // that sit inside an already-retargeted fully-qualified form are dropped by the dedupe.
            for (userType in ktFile.collectDescendantsOfType<KtUserType>()) {
                if (userType.qualifier == null && userType.referencedName == oldSimple) {
                    val nameId = userType.referenceExpression?.getReferencedNameElement() ?: continue
                    edits.add(TextEdit(nameId.textOffset, nameId.textLength, newSimple))
                }
            }
            for (ref in ktFile.collectDescendantsOfType<KtNameReferenceExpression>()) {
                if (ref.getReferencedName() == oldSimple) {
                    edits.add(TextEdit(ref.textOffset, ref.textLength, newSimple))
                }
            }
        }

        return dedupeNonOverlapping(edits)
    }

    private fun dedupeNonOverlapping(edits: List<TextEdit>): List<TextEdit> {
        // Keep the longest edit covering a region and drop any whose range overlaps one already kept,
        // so a simple-name edit contained in a fully-qualified edit (or a duplicate) can't corrupt it.
        val sorted = edits.distinct().sortedByDescending { it.length }
        val kept = mutableListOf<TextEdit>()
        for (edit in sorted) {
            val overlaps = kept.any { edit.offset < it.offset + it.length && it.offset < edit.offset + edit.length }
            if (!overlaps) kept.add(edit)
        }
        return kept
    }

    private fun fileNameOf(path: String): String = path.substringAfterLast('/')
}
