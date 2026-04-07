package no.f12.codenavigator.navigation.refactor

import org.openrewrite.ExecutionContext
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.SourceFile
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.KotlinIsoVisitor
import org.openrewrite.kotlin.KotlinParser
import java.io.File

data class RenameChange(
    val filePath: String,
    val before: String,
    val after: String,
)

data class CascadeCandidate(
    val className: String,
    val methodName: String,
    val paramName: String,
)

data class RenameResult(
    val changes: List<RenameChange>,
    val cascadeCandidates: List<CascadeCandidate> = emptyList(),
) {
    fun toJson(): String {
        val changesJson = if (changes.isEmpty()) {
            "[]"
        } else {
            changes.joinToString(",", "[", "]") { change ->
                val escapedPath = jsonEscape(change.filePath)
                val escapedBefore = jsonEscape(change.before)
                val escapedAfter = jsonEscape(change.after)
                """{"filePath":"$escapedPath","before":"$escapedBefore","after":"$escapedAfter"}"""
            }
        }
        val cascadeJson = if (cascadeCandidates.isNotEmpty()) {
            val candidates = cascadeCandidates.joinToString(",", "[", "]") { c ->
                """{"className":"${jsonEscape(c.className)}","methodName":"${jsonEscape(c.methodName)}","paramName":"${jsonEscape(c.paramName)}"}"""
            }
            ""","cascadeCandidates":$candidates"""
        } else {
            ""
        }
        return """{"changes":$changesJson$cascadeJson}"""
    }

    companion object {
        fun fromJson(json: String): RenameResult {
            val obj = parseJsonObject(json)
            val changesArr = obj["changes"] as? List<*> ?: return RenameResult(emptyList())
            val changes = changesArr.map { item ->
                @Suppress("UNCHECKED_CAST")
                val map = item as Map<String, Any?>
                RenameChange(
                    filePath = map["filePath"] as String,
                    before = map["before"] as String,
                    after = map["after"] as String,
                )
            }
            val cascadeArr = obj["cascadeCandidates"] as? List<*> ?: emptyList<Any>()
            val cascadeCandidates = cascadeArr.map { item ->
                @Suppress("UNCHECKED_CAST")
                val map = item as Map<String, Any?>
                CascadeCandidate(
                    className = map["className"] as String,
                    methodName = map["methodName"] as String,
                    paramName = map["paramName"] as String,
                )
            }
            return RenameResult(changes, cascadeCandidates)
        }
    }
}



object RenameParamRewriter {

    fun rename(
        sourceRoots: List<File>,
        className: String,
        methodName: String,
        paramName: String,
        newName: String,
        preview: Boolean = false,
    ): RenameResult {
        val sourceFiles = collectSourceFiles(sourceRoots)
        if (sourceFiles.isEmpty()) return RenameResult(emptyList())

        val parser = KotlinParser.builder().build()
        val ctx = InMemoryExecutionContext { it.printStackTrace() }

        val parsed = parser.parse(
            sourceFiles.map { it.toPath() },
            null,
            ctx,
        ).toList()

        val visitor = RenameParamVisitor(className, methodName, paramName, newName)

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

        return RenameResult(changes, visitor.cascadeCandidates.toList())
    }

}

private class RenameParamVisitor(
    private val className: String,
    private val methodName: String,
    private val paramName: String,
    private val newName: String,
) : KotlinIsoVisitor<ExecutionContext>() {

    private var inTargetClass = false
    private var inTargetMethod = false
    val cascadeCandidates = mutableSetOf<CascadeCandidate>()

    override fun visitClassDeclaration(
        classDecl: J.ClassDeclaration,
        ctx: ExecutionContext,
    ): J.ClassDeclaration {
        val fqn = classDecl.type?.fullyQualifiedName
        val wasInTarget = inTargetClass
        inTargetClass = fqn == className
        val result = super.visitClassDeclaration(classDecl, ctx)
        inTargetClass = wasInTarget
        return result
    }

    override fun visitMethodDeclaration(
        method: J.MethodDeclaration,
        ctx: ExecutionContext,
    ): J.MethodDeclaration {
        val wasInTargetMethod = inTargetMethod
        if (inTargetClass && method.simpleName == methodName) {
            inTargetMethod = true
        }
        var m = super.visitMethodDeclaration(method, ctx)
        inTargetMethod = wasInTargetMethod

        if (!inTargetClass || m.simpleName != methodName) return m

        val newParams = m.parameters.map { param ->
            if (param is J.VariableDeclarations) {
                val vars = param.variables
                if (vars.size == 1 && vars[0].simpleName == paramName) {
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

    override fun visitIdentifier(
        ident: J.Identifier,
        ctx: ExecutionContext,
    ): J.Identifier {
        val i = super.visitIdentifier(ident, ctx)
        if (!inTargetMethod) return i
        if (i.simpleName != paramName) return i

        // Don't rename named argument keys (left side of J.Assignment in method calls).
        // Named argument renaming for calls to the target method is handled by visitMethodInvocation.
        val parentCursor = cursor.parentTreeCursor
        val parent = parentCursor.getValue<Any>()
        if (parent is J.Assignment) {
            val variable = parent.variable
            // Use ID to distinguish left (key) from right (value) side of named argument
            if (variable is J.Identifier && variable.id == ident.id) {
                return i
            }
        }

        // Check if this identifier is a reference to the parameter
        // (variable type matches, or it's used in a context where it refers to the param)
        val fieldAccess = i.fieldType
        if (fieldAccess != null && fieldAccess.name == paramName) {
            return i.withSimpleName(newName).withFieldType(fieldAccess.withName(newName))
        }

        return i.withSimpleName(newName)
    }

    override fun visitMethodInvocation(
        method: J.MethodInvocation,
        ctx: ExecutionContext,
    ): J.MethodInvocation {
        var m = super.visitMethodInvocation(method, ctx)

        val methodType = m.methodType
        val targetType = methodType?.declaringType?.fullyQualifiedName
        val targetMethod = m.simpleName

        // Detect cascade candidates: calls inside the target method body
        // where the renamed param is forwarded to a same-named param on another method
        if (inTargetMethod && methodType != null) {
            val calledParamNames = methodType.parameterNames ?: emptyList()
            for ((index, arg) in m.arguments.withIndex()) {
                val argIdent = when (arg) {
                    is J.Identifier -> arg
                    is J.Assignment -> (arg.assignment as? J.Identifier)
                    else -> null
                }
                if (argIdent != null && (argIdent.simpleName == paramName || argIdent.simpleName == newName) && index < calledParamNames.size) {
                    val calledParamName = calledParamNames[index]
                    if (calledParamName == paramName) {
                        val calledClassName = targetType ?: "unknown"
                        cascadeCandidates.add(CascadeCandidate(calledClassName, targetMethod, calledParamName))
                    }
                }
            }
        }

        if (targetType != className || targetMethod != methodName) return m

        val newArgs = m.arguments.map { arg ->
            if (arg is J.Assignment) {
                val variable = arg.variable
                if (variable is J.Identifier && variable.simpleName == paramName) {
                    arg.withVariable(variable.withSimpleName(newName))
                } else {
                    arg
                }
            } else {
                arg
            }
        }
        return m.withArguments(newArgs)
    }
}
