package no.f12.codenavigator.navigation.branches

import no.f12.codenavigator.navigation.refactor.withKotlinPsiFactory
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import java.io.File

/**
 * Reads the branch structure of every function in Kotlin source with the compiler's PSI (parsing only, no
 * type resolution, so it also works on code that doesn't compile). Local functions and object literals are
 * left out of a function's body: they run on their own terms. Lambda bodies are kept inline after the call
 * that takes them.
 *
 * Must run with `kotlin-compiler-embeddable` on the classpath (Gradle: inside the PSI worker).
 */
object BranchScanner {

    private const val MAX_CONDITION = 80
    private const val MAX_EXIT = 70

    fun scan(files: List<File>): List<FunctionBranches> {
        val kotlinFiles = files.filter { it.isFile && it.extension == "kt" }
        if (kotlinFiles.isEmpty()) return emptyList()
        return withKotlinPsiFactory("branch-scanner") { factory ->
            kotlinFiles.flatMap { file ->
                val content = file.readText().replace("\r\n", "\n")
                scanFile(factory.createFile(file.name, content), content)
            }
        }
    }

    fun scanSource(fileName: String, content: String): List<FunctionBranches> {
        val normalized = content.replace("\r\n", "\n")
        return withKotlinPsiFactory("branch-scanner") { factory -> scanFile(factory.createFile(fileName, normalized), normalized) }
    }

    private fun scanFile(file: KtFile, content: String): List<FunctionBranches> {
        val lines = LineIndex(content)
        val pkg = file.packageFqName.asString().let { if (it.isEmpty()) "" else "$it." }
        val out = mutableListOf<FunctionBranches>()
        for (declaration in file.declarations) {
            when (declaration) {
                is KtNamedFunction -> function(declaration, pkg + facadeName(file), lines)?.let { out += it }
                is KtClassOrObject -> collectClass(declaration, pkg, lines, out)
            }
        }
        return out
    }

    private fun collectClass(cls: KtClassOrObject, prefix: String, lines: LineIndex, out: MutableList<FunctionBranches>) {
        val simpleName = cls.name ?: if (cls is KtObjectDeclaration && cls.isCompanion()) "Companion" else return
        val className = prefix + simpleName
        for (declaration in cls.declarations) {
            when (declaration) {
                is KtNamedFunction -> function(declaration, className, lines)?.let { out += it }
                is KtClassOrObject -> collectClass(declaration, "$className$", lines, out)
            }
        }
    }

    /** `Things.kt` → `ThingsKt`, or the name given by `@file:JvmName`. */
    private fun facadeName(file: KtFile): String {
        val jvmName = file.fileAnnotationList?.annotationEntries
            ?.firstOrNull { it.shortName?.asString() == "JvmName" }
            ?.valueArguments?.firstOrNull()?.getArgumentExpression()
            ?.let { (it as? KtStringTemplateExpression)?.entries?.joinToString("") { entry -> entry.text } }
        return jvmName ?: (file.name.removeSuffix(".kt").replaceFirstChar { it.uppercase() } + "Kt")
    }

    private fun function(fn: KtNamedFunction, className: String, lines: LineIndex): FunctionBranches? {
        val name = fn.name ?: return null
        val body = fn.bodyExpression?.let { Walker(lines).items(it) } ?: emptyList()
        return FunctionBranches(className, name, lines.line(fn.textRange.startOffset), lines.line(fn.textRange.endOffset), body)
    }

    private class Walker(private val lines: LineIndex) {

        fun items(element: PsiElement?): List<BranchItem> = mutableListOf<BranchItem>().also { visit(element, it) }

        private fun line(element: PsiElement) = lines.line(element.textRange.startOffset)

        private fun visit(element: PsiElement?, out: MutableList<BranchItem>) {
            when (element) {
                null -> {}
                is KtNamedFunction, is KtClassOrObject, is KtObjectLiteralExpression -> {}
                is KtIfExpression -> ifChain(element, out)
                is KtWhenExpression -> whenExpression(element, out)
                is KtTryExpression -> tryExpression(element, out)
                is KtForExpression -> {
                    visit(element.loopRange, out)
                    val label = "for (${oneLine(element.loopParameter?.text)} in ${oneLine(element.loopRange?.text)})"
                    out += Branch(BranchKind.LOOP, shorten(label, MAX_CONDITION + 9), line(element), items(element.body))
                }
                is KtDoWhileExpression -> {
                    val children = items(element.body) + items(element.condition)
                    out += Branch(BranchKind.LOOP, "do … while (${condition(element.condition)})", line(element), children)
                }
                is KtWhileExpression -> {
                    val children = items(element.condition) + items(element.body)
                    out += Branch(BranchKind.LOOP, "while (${condition(element.condition)})", line(element), children)
                }
                is KtReturnExpression -> {
                    visit(element.returnedExpression, out)
                    out += Exit(ExitKind.RETURN, shorten(oneLine(element.text), MAX_EXIT), line(element))
                }
                is KtThrowExpression -> {
                    visit(element.thrownExpression, out)
                    out += Exit(ExitKind.THROW, shorten(oneLine(element.text), MAX_EXIT), line(element))
                }
                is KtBinaryExpression -> if (element.operationToken == KtTokens.ELVIS) {
                    visit(element.left, out)
                    out += Branch(BranchKind.ELVIS, "if (${condition(element.left)} == null)", line(element.operationReference), items(element.right))
                } else {
                    visitChildren(element, out)
                }
                is KtSafeQualifiedExpression -> {
                    visit(element.receiverExpression, out)
                    val selector = mutableListOf<BranchItem>().also { selector(element, it) }
                    out += Branch(BranchKind.SAFE_CALL, "if (${condition(element.receiverExpression)} != null)", line(element.operationTokenNode.psi), selector)
                }
                is KtDotQualifiedExpression -> {
                    visit(element.receiverExpression, out)
                    selector(element, out)
                }
                is KtCallExpression -> call(element, out)
                else -> visitChildren(element, out)
            }
        }

        private fun visitChildren(element: PsiElement, out: MutableList<BranchItem>) {
            var child = element.firstChild
            while (child != null) {
                visit(child, out)
                child = child.nextSibling
            }
        }

        private fun selector(qualified: KtQualifiedExpression, out: MutableList<BranchItem>) {
            when (val selector = qualified.selectorExpression) {
                is KtNameReferenceExpression -> out += CallSite(selector.getReferencedName(), line(selector), property = true)
                else -> visit(selector, out)
            }
        }

        private fun call(call: KtCallExpression, out: MutableList<BranchItem>) {
            val callee = call.calleeExpression
            if (callee !is KtNameReferenceExpression) visit(callee, out)
            for (argument in call.valueArguments) {
                if (argument.getArgumentExpression() !in call.lambdaArguments.map { it.getLambdaExpression() }) {
                    visit(argument.getArgumentExpression(), out)
                }
            }
            if (callee is KtNameReferenceExpression) out += CallSite(callee.getReferencedName(), line(callee))
            for (lambda in call.lambdaArguments) visit(lambda.getLambdaExpression()?.bodyExpression, out)
        }

        private fun ifChain(first: KtIfExpression, out: MutableList<BranchItem>) {
            visit(first.condition, out)
            out += Branch(BranchKind.IF, "if (${condition(first.condition)})", line(first), items(first.then))
            var elseExpr: KtExpression? = first.`else`
            while (elseExpr != null) {
                if (elseExpr is KtIfExpression) {
                    val arm = items(elseExpr.condition) + items(elseExpr.then)
                    out += Branch(BranchKind.IF, "else if (${condition(elseExpr.condition)})", line(elseExpr), arm)
                    elseExpr = elseExpr.`else`
                } else {
                    out += Branch(BranchKind.ELSE, "else", line(elseExpr), items(elseExpr))
                    elseExpr = null
                }
            }
        }

        private fun whenExpression(expr: KtWhenExpression, out: MutableList<BranchItem>) {
            val subject = expr.subjectVariable ?: expr.subjectExpression
            visit(subject, out)
            val label = if (subject == null) "when" else "when (${condition(subject)})"
            val entries = expr.entries.map { entry ->
                val entryLabel = if (entry.isElse) "else ->" else shorten(entry.conditions.joinToString(", ") { oneLine(it.text) }, MAX_CONDITION) + " ->"
                val children = entry.conditions.flatMap { items(it) } + items(entry.expression ?: entry)
                Branch(BranchKind.WHEN_ENTRY, entryLabel, line(entry), children)
            }
            out += Branch(BranchKind.WHEN, label, line(expr), entries)
        }

        private fun tryExpression(expr: KtTryExpression, out: MutableList<BranchItem>) {
            out += Branch(BranchKind.TRY, "try", line(expr), items(expr.tryBlock))
            for (catch in expr.catchClauses) {
                out += Branch(BranchKind.CATCH, "catch (${oneLine(catch.catchParameter?.text)})", line(catch), items(catch.catchBody ?: catch))
            }
            expr.finallyBlock?.let { out += Branch(BranchKind.FINALLY, "finally", line(it), items(it.finalExpression)) }
        }

        private fun condition(expr: PsiElement?): String = shorten(oneLine(expr?.text), MAX_CONDITION)
    }

    private fun oneLine(text: String?): String = (text ?: "").replace(Regex("\\s+"), " ").trim()

    private fun shorten(text: String, max: Int): String = if (text.length <= max) text else text.take(max - 1) + "…"

    private class LineIndex(content: String) {
        private val lineStarts: IntArray = buildList {
            add(0)
            content.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
        }.toIntArray()

        /** 1-based line of [offset]. */
        fun line(offset: Int): Int {
            val index = lineStarts.binarySearch(offset)
            return (if (index >= 0) index else -index - 2) + 1
        }
    }
}
