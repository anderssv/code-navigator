package no.f12.codenavigator.navigation.refactor

import org.openrewrite.ExecutionContext
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.SourceFile
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.KotlinIsoVisitor
import org.openrewrite.kotlin.KotlinParser
import java.io.File

data class RenameMethodResult(
    val changes: List<RenameChange>,
) {
    fun toJson(): String = """{"changes":${changesToJson(changes)}}"""

    companion object {
        fun fromJson(json: String): RenameMethodResult {
            val obj = parseJsonObject(json)
            return RenameMethodResult(changesFromJson(obj))
        }
    }
}

object RenameMethodRewriter {

    fun rename(
        sourceRoots: List<File>,
        className: String,
        methodName: String,
        newName: String,
        preview: Boolean = false,
    ): RenameMethodResult {
        val sourceFiles = collectSourceFiles(sourceRoots)
        if (sourceFiles.isEmpty()) return RenameMethodResult(emptyList())

        val parser = KotlinParser.builder().build()
        val ctx = InMemoryExecutionContext { it.printStackTrace() }

        val parsed = parser.parse(
            sourceFiles.map { it.toPath() },
            null,
            ctx,
        ).toList()

        val visitor = RenameMethodVisitor(className, methodName, newName)

        val changes = mutableListOf<RenameChange>()
        for (sourceFile in parsed) {
            val before = sourceFile.printAll()
            val modified = visitor.visit(sourceFile, ctx) as? SourceFile ?: continue
            val after = modified.printAll()
            if (before != after) {
                val filePath = resolveOriginalPath(sourceFile, sourceRoots)
                changes.add(RenameChange(filePath, before, after))
            }
        }

        if (!preview) {
            for (change in changes) {
                File(change.filePath).writeText(change.after)
            }
        }

        return RenameMethodResult(changes)
    }

}

private class RenameMethodVisitor(
    private val className: String,
    private val methodName: String,
    private val newName: String,
) : KotlinIsoVisitor<ExecutionContext>() {

    private var inTargetClassOrImplementor = false

    override fun visitClassDeclaration(
        classDecl: J.ClassDeclaration,
        ctx: ExecutionContext,
    ): J.ClassDeclaration {
        val wasInTarget = inTargetClassOrImplementor
        inTargetClassOrImplementor = isTargetOrImplementor(classDecl)
        val result = super.visitClassDeclaration(classDecl, ctx)
        inTargetClassOrImplementor = wasInTarget
        return result
    }

    private fun isTargetOrImplementor(classDecl: J.ClassDeclaration): Boolean {
        val classType = classDecl.type ?: return false
        if (classType.fullyQualifiedName == className) return true
        return classType.interfaces.any { it.fullyQualifiedName == className }
            || classType.supertype?.fullyQualifiedName == className
    }

    override fun visitMethodDeclaration(
        method: J.MethodDeclaration,
        ctx: ExecutionContext,
    ): J.MethodDeclaration {
        val m = super.visitMethodDeclaration(method, ctx)
        if (!inTargetClassOrImplementor || m.simpleName != methodName) return m
        return m.withName(m.name.withSimpleName(newName))
    }

    override fun visitMethodInvocation(
        method: J.MethodInvocation,
        ctx: ExecutionContext,
    ): J.MethodInvocation {
        val m = super.visitMethodInvocation(method, ctx)
        val targetType = m.methodType?.declaringType?.fullyQualifiedName
        if (targetType != className || m.simpleName != methodName) return m
        return m.withName(m.name.withSimpleName(newName))
    }
}
