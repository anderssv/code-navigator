package no.f12.codenavigator.navigation.refactor

import org.openrewrite.ExecutionContext
import org.openrewrite.SourceFile
import org.openrewrite.java.tree.Expression
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaType
import org.openrewrite.kotlin.KotlinIsoVisitor
import java.io.File

data class RenamePropertyResult(
    val changes: List<RenameChange>,
) {
    fun toJson(): String = """{"changes":${changesToJson(changes)}}"""

    companion object {
        fun fromJson(json: String): RenamePropertyResult {
            val obj = parseJsonObject(json)
            return RenamePropertyResult(changesFromJson(obj))
        }
    }
}

object RenamePropertyRewriter {

    fun rename(
        sourceRoots: List<File>,
        className: String,
        propertyName: String,
        newName: String,
        preview: Boolean = false,
        parsedSources: ParsedSources? = null,
    ): RenamePropertyResult {
        val ps = parsedSources ?: run {
            val sourceFiles = collectSourceFiles(sourceRoots)
            if (sourceFiles.isEmpty()) return RenamePropertyResult(emptyList())
            parseKotlinSources(sourceRoots)
        }
        if (ps.sources.isEmpty()) return RenamePropertyResult(emptyList())

        val visitor = RenamePropertyVisitor(className, propertyName, newName)

        val changes = mutableListOf<RenameChange>()
        for (sourceFile in ps.sources) {
            val before = sourceFile.printAll()
            val modified = visitor.visit(sourceFile, ps.ctx) as? SourceFile ?: continue
            val after = modified.printAll()
            if (before != after) {
                val filePath = resolveOriginalPath(sourceFile, ps.sourceRoots)
                changes.add(RenameChange(filePath, before, after))
            }
        }

        if (!preview) {
            for (change in changes) {
                File(change.filePath).writeText(change.after)
            }
        }

        return RenamePropertyResult(changes)
    }
}

private class RenamePropertyVisitor(
    private val className: String,
    private val propertyName: String,
    private val newName: String,
) : KotlinIsoVisitor<ExecutionContext>() {

    private var inTargetClass = false

    override fun visitClassDeclaration(
        classDecl: J.ClassDeclaration,
        ctx: ExecutionContext,
    ): J.ClassDeclaration {
        val fqn = classDecl.type?.fullyQualifiedName
        val wasInTarget = inTargetClass
        inTargetClass = matchesClassOrCompanion(fqn, className)
        val result = super.visitClassDeclaration(classDecl, ctx)
        inTargetClass = wasInTarget
        return result
    }

    override fun visitMethodDeclaration(
        method: J.MethodDeclaration,
        ctx: ExecutionContext,
    ): J.MethodDeclaration {
        if (!inTargetClass) return super.visitMethodDeclaration(method, ctx)

        // Rename constructor parameter (val/var in primary constructor)
        if (method.simpleName == "<constructor>") {
            val m = super.visitMethodDeclaration(method, ctx)
            val newParams = m.parameters.map { param ->
                if (param is J.VariableDeclarations) {
                    val vars = param.variables
                    if (vars.size == 1 && vars[0].simpleName == propertyName) {
                        param.withVariables(
                            vars.map { v ->
                                v.withName(v.name.withSimpleName(newName))
                            },
                        )
                    } else {
                        param
                    }
                } else {
                    param
                }
            }
            return m.withParameters(newParams)
        }

        return super.visitMethodDeclaration(method, ctx)
    }

    override fun visitMethodInvocation(
        method: J.MethodInvocation,
        ctx: ExecutionContext,
    ): J.MethodInvocation {
        val m = super.visitMethodInvocation(method, ctx)
        val targetType = m.methodType?.declaringType?.fullyQualifiedName

        // Rename named arguments at any call site on the target class
        // (copy, factory methods, etc.)
        if (matchesClassOrCompanion(targetType, className)) {
            return m.withArguments(renameNamedArgs(m.arguments))
        }

        return m
    }

    override fun visitNewClass(
        newClass: J.NewClass,
        ctx: ExecutionContext,
    ): J.NewClass {
        val nc = super.visitNewClass(newClass, ctx)
        val constructorType = nc.constructorType?.declaringType?.fullyQualifiedName

        // Rename named arguments at constructor call sites
        if (matchesClassOrCompanion(constructorType, className)) {
            return nc.withArguments(renameNamedArgs(nc.arguments ?: emptyList()))
        }

        return nc
    }

    private fun renameNamedArgs(args: List<Expression>): List<Expression> =
        args.map { arg ->
            if (arg is J.Assignment) {
                val variable = arg.variable
                if (variable is J.Identifier && variable.simpleName == propertyName) {
                    arg.withVariable(variable.withSimpleName(newName))
                } else {
                    arg
                }
            } else {
                arg
            }
        }

    override fun visitIdentifier(
        ident: J.Identifier,
        ctx: ExecutionContext,
    ): J.Identifier {
        val i = super.visitIdentifier(ident, ctx)
        if (i.simpleName != propertyName) return i

        // Check if this is a property access on the target class (e.g., instance.fullName)
        val fieldType = i.fieldType
        if (fieldType != null && fieldType.name == propertyName) {
            val ownerFqn = (fieldType.owner as? JavaType.FullyQualified)?.fullyQualifiedName
            if (matchesClassOrCompanion(ownerFqn, className)) {
                return i.withSimpleName(newName).withFieldType(fieldType.withName(newName))
            }
        }

        return i
    }
}
