package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File
import java.nio.file.Path

/**
 * PSI-based replacement for OpenRewrite's `ChangeType`: retargets every reference to a fully-qualified
 * type from [oldFqcn] to [newFqcn] across a set of source files. Covers both dimensions a move can carry:
 *
 *  - **package change** — rewrites the fully-qualified forms: import directives
 *    (`import old.pkg.Foo` / `... as Bar`, alias preserved) and fully-qualified references in code
 *    (`val x: old.pkg.Foo`, `old.pkg.Foo()`), matched on exact text so a longer path that merely starts
 *    with the FQN is never touched.
 *  - **simple-name change (rename)** — when the simple name differs, also renames the class/interface/object
 *    *declaration* (matched by exact FQN, so a same-named enum entry or nested type is never mistaken for it)
 *    and every unqualified simple-name reference to it. References are confirmed by K1 semantic resolution
 *    when a classpath is available (only those that actually resolve to the moved type are renamed), falling
 *    back to a same-package-or-imports heuristic otherwise. Trailing segments of already-retargeted
 *    fully-qualified references are dropped by the overlap dedupe so the two passes never collide.
 *
 * It deliberately does *not* touch the declaring file's package declaration or add imports for now-non-local
 * siblings — those are handled by the caller's textual passes, unchanged. Unlike OpenRewrite's
 * `KotlinIsoVisitor`, plain PSI traversal reaches references at any lambda-nesting depth.
 */
object KotlinTypeReferenceRewriter {

    /**
     * Returns path -> new content for every file whose references changed. Files with no reference to
     * [oldFqcn] are omitted.
     *
     * When [sourceRoots] and [classpath] are provided *and* this is a rename (simple name changes), the
     * unqualified simple-name references are confirmed by K1 semantic resolution ([KotlinReferenceResolver])
     * — only references that actually resolve to [oldFqcn] are renamed, so a shadowing local or a same-named
     * type in another package is left alone. If resolution can't be built (no classpath, analysis fails),
     * it falls back to the heuristic (same-package-or-imports gating). Imports and fully-qualified references
     * are exact-FQN and never need resolution.
     */
    fun retargetAcrossSources(
        sources: List<SourceFileContent>,
        oldFqcn: String,
        newFqcn: String,
        sourceRoots: List<File> = emptyList(),
        classpath: List<Path> = emptyList(),
    ): Map<String, String> {
        val oldPackage = oldFqcn.substringBeforeLast('.', "")
        val oldSimple = oldFqcn.substringAfterLast('.')
        val newSimple = newFqcn.substringAfterLast('.')

        // Cheap pre-filter: a file can only reference the type if its old package (or the simple name)
        // appears in the text — avoids parsing files that obviously can't match.
        val candidates = sources.filter { it.content.contains(oldPackage.ifEmpty { oldFqcn }) || it.content.contains(oldSimple) }
        if (candidates.isEmpty()) return emptyMap()
        val candidatePaths = candidates.map { it.path }.toSet()
        val contentByPath = sources.associate { it.path to it.content }

        // Resolution is only worth its cost on the rename pass (the sole false-positive risk).
        val resolver = if (oldSimple != newSimple && classpath.isNotEmpty()) {
            KotlinReferenceResolver.tryBuild(sourceRoots, classpath)
        } else {
            null
        }

        val changed = mutableMapOf<String, String>()
        try {
            if (resolver != null) {
                // Reuse the files parsed inside the analysis session so PSI offsets line up with the
                // BindingContext. Their on-disk text matches `sources` here (single-move path).
                for ((path, ktFile) in resolver.ktFilesByPath) {
                    if (path !in candidatePaths) continue
                    val content = contentByPath[path] ?: continue
                    val edits = collectEdits(ktFile, oldFqcn, newFqcn, oldSimple, newSimple, resolver)
                    if (edits.isEmpty()) continue
                    val updated = applyEdits(content, edits)
                    if (updated != content) changed[path] = updated
                }
            } else {
                withKotlinPsiFactory("move-class-retarget") { psiFactory ->
                    for (source in candidates) {
                        val ktFile = psiFactory.createFile(fileNameOf(source.path), source.content)
                        val edits = collectEdits(ktFile, oldFqcn, newFqcn, oldSimple, newSimple, resolver = null)
                        if (edits.isEmpty()) continue
                        val updated = applyEdits(source.content, edits)
                        if (updated != source.content) changed[source.path] = updated
                    }
                }
            }
        } finally {
            resolver?.close()
        }
        return changed
    }

    private fun collectEdits(
        ktFile: KtFile,
        oldFqcn: String,
        newFqcn: String,
        oldSimple: String,
        newSimple: String,
        resolver: KotlinReferenceResolver?,
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

        if (oldSimple == newSimple) return dedupeNonOverlapping(edits)

        // --- Simple-name rename ---
        // With a resolver, each reference is confirmed per-occurrence (resolves to oldFqcn). Without one,
        // fall back to the heuristic file-level gate (same package or imports the type).
        if (resolver == null && !fileReferencesClass(ktFile, oldFqcn)) return dedupeNonOverlapping(edits)

        // The declaration itself. Match the exact declared FQN (not just the simple name) so a
        // same-named nested type or enum entry — e.g. `enum class Kind { Widget }` when moving a
        // top-level `Widget` — is never mistaken for the declaration. (KtEnumEntry is a KtClassOrObject
        // whose fqName is `pkg.Kind.Widget`, so it won't equal the moved type's `pkg.Widget`.)
        for (decl in ktFile.collectDescendantsOfType<KtClassOrObject>()) {
            if (decl.fqName?.asString() != oldFqcn) continue
            val nameId = decl.nameIdentifier ?: continue
            edits.add(TextEdit(nameId.textOffset, nameId.textLength, newSimple))
        }
        // Unqualified references: type positions and expression positions. Contained references that sit
        // inside an already-retargeted fully-qualified form are dropped by the dedupe.
        for (userType in ktFile.collectDescendantsOfType<KtUserType>()) {
            if (userType.qualifier != null || userType.referencedName != oldSimple) continue
            val refExpr = userType.referenceExpression ?: continue
            if (!referenceResolvesToTarget(refExpr, oldFqcn, resolver)) continue
            val nameId = refExpr.getReferencedNameElement()
            edits.add(TextEdit(nameId.textOffset, nameId.textLength, newSimple))
        }
        for (ref in ktFile.collectDescendantsOfType<KtNameReferenceExpression>()) {
            if (ref.getReferencedName() != oldSimple) continue
            if (!referenceResolvesToTarget(ref, oldFqcn, resolver)) continue
            edits.add(TextEdit(ref.textOffset, ref.textLength, newSimple))
        }

        return dedupeNonOverlapping(edits)
    }

    /** With a resolver, keep the reference only if it semantically resolves to [oldFqcn]; without one, keep it (heuristic). */
    private fun referenceResolvesToTarget(ref: KtReferenceExpression, oldFqcn: String, resolver: KotlinReferenceResolver?): Boolean =
        resolver == null || resolver.resolvedClassFqn(ref) == oldFqcn

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
