package no.f12.codenavigator.navigation.declaration

import no.f12.codenavigator.navigation.refactor.withKotlinPsiFactory
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias
import java.io.File

/**
 * Walks every `.kt` file under the given source roots with the Kotlin compiler's PSI and reports the
 * exact text range of each class-level and top-level declaration.
 *
 * Parsing only — no type resolution, no `BindingContext` — so it works on code that doesn't compile.
 * Local declarations inside function bodies are deliberately skipped: they are not addressable by
 * a stable name and are not what a refactoring agent asks for.
 *
 * Must run with `kotlin-compiler-embeddable` on the classpath (Gradle: inside the PSI worker).
 */
object DeclarationScanner {

    fun scan(sourceRoots: List<File>): List<DeclarationInfo> {
        val files = sourceRoots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { root to it }
                .toList()
        }
        if (files.isEmpty()) return emptyList()

        return withKotlinPsiFactory("declaration-scanner") { psiFactory ->
            files.flatMap { (root, file) ->
                val content = file.readText().replace("\r\n", "\n")
                val ktFile = psiFactory.createFile(file.name, content)
                val sourcePath = file.relativeTo(root).path.replace(File.separatorChar, '/')
                scanFile(ktFile, content, file.absolutePath, sourcePath)
            }
        }.sortedWith(compareBy({ it.file }, { it.startOffset }))
    }

    /** Scans a single already-parsed file. Exposed for tests that build PSI themselves. */
    fun scanFile(ktFile: KtFile, content: String, filePath: String, sourcePath: String): List<DeclarationInfo> {
        val lines = LineIndex(content)
        val packagePrefix = ktFile.packageFqName.asString().let { if (it.isEmpty()) "" else "$it." }
        val out = mutableListOf<DeclarationInfo>()
        for (declaration in ktFile.declarations) {
            collect(declaration, packagePrefix, lines, filePath, sourcePath, out)
        }
        return out
    }

    private fun collect(
        declaration: KtDeclaration,
        ownerPrefix: String,
        lines: LineIndex,
        filePath: String,
        sourcePath: String,
        out: MutableList<DeclarationInfo>,
    ) {
        val kind = kindOf(declaration) ?: return
        val name = nameOf(declaration, kind) ?: return
        val fqn = ownerPrefix + name
        out += toInfo(declaration, kind, name, fqn, lines, filePath, sourcePath)

        if (declaration is KtClassOrObject) {
            val memberPrefix = "$fqn."
            declaration.primaryConstructorParameters
                .filter { it.hasValOrVar() }
                .forEach { param ->
                    val paramName = param.name ?: return@forEach
                    out += toInfo(param, DeclarationKind.PROPERTY, paramName, memberPrefix + paramName, lines, filePath, sourcePath)
                }
            for (member in declaration.declarations) {
                collect(member, memberPrefix, lines, filePath, sourcePath, out)
            }
        }
    }

    private fun kindOf(declaration: KtDeclaration): DeclarationKind? = when (declaration) {
        is KtEnumEntry -> DeclarationKind.ENUM_ENTRY
        is KtClass -> when {
            declaration.isInterface() -> DeclarationKind.INTERFACE
            declaration.isEnum() -> DeclarationKind.ENUM
            declaration.isAnnotation() -> DeclarationKind.ANNOTATION
            else -> DeclarationKind.CLASS
        }
        is KtObjectDeclaration -> if (declaration.isCompanion()) DeclarationKind.COMPANION_OBJECT else DeclarationKind.OBJECT
        is KtNamedFunction -> DeclarationKind.FUNCTION
        is KtSecondaryConstructor -> DeclarationKind.CONSTRUCTOR
        is KtProperty -> DeclarationKind.PROPERTY
        is KtTypeAlias -> DeclarationKind.TYPEALIAS
        else -> null
    }

    private fun nameOf(declaration: KtDeclaration, kind: DeclarationKind): String? = when (kind) {
        DeclarationKind.COMPANION_OBJECT -> (declaration as KtObjectDeclaration).name ?: "Companion"
        DeclarationKind.CONSTRUCTOR -> "<init>"
        else -> (declaration as? KtNamedDeclaration)?.name
    }

    private fun signatureOf(declaration: KtDeclaration, name: String): String = when (declaration) {
        is KtNamedFunction -> buildString {
            declaration.receiverTypeReference?.let { append(it.text).append('.') }
            append(name)
            append(declaration.valueParameterList?.text ?: "()")
            declaration.typeReference?.let { append(": ").append(it.text) }
        }
        is KtSecondaryConstructor -> "constructor" + (declaration.valueParameterList?.text ?: "()")
        is KtProperty -> buildString {
            append(if (declaration.isVar) "var " else "val ")
            declaration.receiverTypeReference?.let { append(it.text).append('.') }
            append(name)
            declaration.typeReference?.let { append(": ").append(it.text) }
        }
        is KtParameter -> buildString {
            append(if (declaration.isMutable) "var " else "val ")
            append(name)
            declaration.typeReference?.let { append(": ").append(it.text) }
        }
        is KtClassOrObject -> name + (declaration.primaryConstructor?.valueParameterList?.text ?: "")
        is KtTypeAlias -> "typealias $name = ${declaration.getTypeReference()?.text ?: "?"}"
        else -> name
    }

    private fun toInfo(
        declaration: KtDeclaration,
        kind: DeclarationKind,
        name: String,
        fqn: String,
        lines: LineIndex,
        filePath: String,
        sourcePath: String,
    ): DeclarationInfo {
        val range = declaration.textRange
        val nameOffset = (declaration as? KtNamedDeclaration)?.nameIdentifier?.textRange?.startOffset ?: range.startOffset
        val (startLine, startColumn) = lines.position(range.startOffset)
        // endOffset is exclusive; report the line/column of the last character so a reader
        // sees the closing brace's own line rather than the one after it.
        val (endLine, endColumn) = lines.position(maxOf(range.startOffset, range.endOffset - 1))
        return DeclarationInfo(
            kind = kind,
            fqn = fqn,
            name = name,
            signature = signatureOf(declaration, name),
            file = filePath,
            sourcePath = sourcePath,
            startLine = startLine,
            startColumn = startColumn,
            endLine = endLine,
            endColumn = endColumn,
            startOffset = range.startOffset,
            endOffset = range.endOffset,
            nameLine = lines.position(nameOffset).first,
        )
    }

    /** Offset → (1-based line, 1-based column) lookup over `\n`-separated content. */
    private class LineIndex(content: String) {
        private val lineStarts: IntArray

        init {
            val starts = mutableListOf(0)
            content.forEachIndexed { i, c -> if (c == '\n') starts += i + 1 }
            lineStarts = starts.toIntArray()
        }

        fun position(offset: Int): Pair<Int, Int> {
            var lo = 0
            var hi = lineStarts.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
            }
            return (lo + 1) to (offset - lineStarts[lo] + 1)
        }
    }
}
