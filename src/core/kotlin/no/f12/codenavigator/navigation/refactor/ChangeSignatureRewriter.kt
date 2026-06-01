package no.f12.codenavigator.navigation.refactor

import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File

data class ChangeSignatureResult(
    val changes: List<RenameChange> = emptyList(),
    val reason: String? = null,
) {
    fun toJson(): String {
        val reasonJson = reason?.let { ""","reason":"${jsonEscape(it)}"""" } ?: ""
        return """{"changes":${changesToJson(changes)}$reasonJson}"""
    }

    companion object {
        fun fromJson(json: String): ChangeSignatureResult {
            val obj = parseJsonObject(json)
            val changes = changesFromJson(obj)
            val reason = obj["reason"] as? String
            return ChangeSignatureResult(changes = changes, reason = reason)
        }
    }
}

data class ParamSpec(
    val name: String,
    val typeAndDefault: String,
)

object ChangeSignatureRewriter {

    fun change(
        sourceRoots: List<File>,
        classDirectories: List<File>,
        className: String,
        methodName: String,
        newParams: String,
        defaults: Map<String, String> = emptyMap(),
        preview: Boolean = false,
    ): ChangeSignatureResult {
        val sourceFiles = sourceRoots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        if (sourceFiles.isEmpty()) return ChangeSignatureResult(reason = "No source files found")

        val targetSimpleName = className.substringAfterLast(".")
        val targetPackage = className.substringBeforeLast(".", "")

        val newParamSpecs = parseParamList(newParams)

        val disposable = Disposer.newDisposable("psi-change-signature")
        try {
            val configuration = CompilerConfiguration().apply {
                put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                    PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false))
                put(CommonConfigurationKeys.MODULE_NAME, "change-signature-target")
            }
            val environment = KotlinCoreEnvironment.createForProduction(
                disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
            val psiFactory = KtPsiFactory(environment.project)

            var currentParams: List<ParamSpec>? = null
            val changes = mutableListOf<RenameChange>()

            // First pass: find the declaration and extract current params
            for (file in sourceFiles) {
                val content = file.readText()
                val ktFile = psiFactory.createFile(file.name, content)
                val filePackage = ktFile.packageFqName.asString()
                if (filePackage != targetPackage) continue

                val classDecls = ktFile.collectDescendantsOfType<KtClassOrObject>()
                for (clazz in classDecls) {
                    if (clazz.name != targetSimpleName) continue
                    val method = clazz.declarations
                        .filterIsInstance<KtNamedFunction>()
                        .firstOrNull { it.name == methodName }
                    if (method == null) {
                        // Also check companion objects
                        val companionMethod = (clazz as? KtClass)?.companionObjects
                            ?.flatMap { it.declarations.filterIsInstance<KtNamedFunction>() }
                            ?.firstOrNull { it.name == methodName }
                        if (companionMethod != null) {
                            currentParams = companionMethod.valueParameters.map { p ->
                                ParamSpec(p.name ?: "", p.text.substringAfter(p.name ?: "").trimStart().removePrefix(":").trim())
                            }
                            val paramList = companionMethod.valueParameterList ?: continue
                            val newParamText = newParamSpecs.joinToString(", ") { "${it.name}: ${it.typeAndDefault}" }
                            val edits = listOf(TextEdit(paramList.textOffset + 1, paramList.textLength - 2, newParamText))
                            val after = applyEdits(content, edits)
                            if (after != content) {
                                changes.add(RenameChange(file.absolutePath, content, after))
                            }
                            break
                        }
                        continue
                    }

                    currentParams = method.valueParameters.map { p ->
                        ParamSpec(p.name ?: "", p.text.substringAfter(p.name ?: "").trimStart().removePrefix(":").trim())
                    }

                    // Rewrite declaration
                    val paramList = method.valueParameterList ?: continue
                    val newParamText = newParamSpecs.joinToString(", ") { "${it.name}: ${it.typeAndDefault}" }
                    val edits = listOf(TextEdit(paramList.textOffset + 1, paramList.textLength - 2, newParamText))
                    val after = applyEdits(content, edits)
                    if (after != content) {
                        changes.add(RenameChange(file.absolutePath, content, after))
                    }
                    break
                }
                if (currentParams != null) break
            }

            if (currentParams == null) {
                return ChangeSignatureResult(reason = "Method not found: $className.$methodName")
            }

            // Check if new params need defaults
            val addedParams = newParamSpecs.filter { new -> currentParams!!.none { it.name == new.name } }
            for (added in addedParams) {
                if (!defaults.containsKey(added.name)) {
                    return ChangeSignatureResult(reason = "No default provided for new parameter '${added.name}'. Use defaults parameter.")
                }
            }

            // Build mapping from old positions to new positions
            val oldParamNames = currentParams!!.map { it.name }
            val newParamNames = newParamSpecs.map { it.name }

            // Second pass: rewrite call sites
            for (file in sourceFiles) {
                val content = file.readText()
                val ktFile = psiFactory.createFile(file.name, content)

                // Skip if we already have a change for this file (declaration file)
                val existingChangeIdx = changes.indexOfFirst { it.filePath == file.absolutePath }
                val workingContent = if (existingChangeIdx >= 0) changes[existingChangeIdx].after else content

                val ktFileForCalls = if (existingChangeIdx >= 0) {
                    psiFactory.createFile(file.name, workingContent)
                } else {
                    ktFile
                }

                val edits = mutableListOf<TextEdit>()
                val callExprs = ktFileForCalls.collectDescendantsOfType<KtCallExpression>()
                for (call in callExprs) {
                    val callee = call.calleeExpression as? KtNameReferenceExpression ?: continue
                    if (callee.getReferencedName() != methodName) continue

                    // Heuristic: check if this is likely a call to our target method
                    // (dot-qualified on an instance, or same-class call)
                    rewriteCallSite(call, oldParamNames, newParamNames, defaults, edits)
                }

                if (edits.isNotEmpty()) {
                    val after = applyEdits(workingContent, edits.distinctBy { it.offset })
                    if (after != workingContent) {
                        val original = if (existingChangeIdx >= 0) changes[existingChangeIdx].before else content
                        if (existingChangeIdx >= 0) {
                            changes[existingChangeIdx] = RenameChange(file.absolutePath, original, after)
                        } else {
                            changes.add(RenameChange(file.absolutePath, content, after))
                        }
                    }
                }
            }

            if (!preview) {
                for (change in changes) {
                    File(change.filePath).writeText(change.after)
                }
            }

            return ChangeSignatureResult(changes = changes)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun rewriteCallSite(
        call: KtCallExpression,
        oldParamNames: List<String>,
        newParamNames: List<String>,
        defaults: Map<String, String>,
        edits: MutableList<TextEdit>,
    ) {
        val argList = call.valueArgumentList ?: return
        val args = call.valueArguments

        // Determine if call uses named args
        val usesNamedArgs = args.any { it.getArgumentName() != null }

        if (usesNamedArgs) {
            rewriteNamedCallSite(argList, args, oldParamNames, newParamNames, defaults, edits)
        } else {
            rewritePositionalCallSite(argList, args, oldParamNames, newParamNames, defaults, edits)
        }
    }

    private fun rewritePositionalCallSite(
        argList: KtValueArgumentList,
        args: List<KtValueArgument>,
        oldParamNames: List<String>,
        newParamNames: List<String>,
        defaults: Map<String, String>,
        edits: MutableList<TextEdit>,
    ) {
        // Map old positional args to their param names
        val argsByOldName = mutableMapOf<String, String>()
        for ((i, arg) in args.withIndex()) {
            if (i < oldParamNames.size) {
                argsByOldName[oldParamNames[i]] = arg.getArgumentExpression()?.text ?: ""
            }
        }

        // Build new arg list in new order
        val newArgs = newParamNames.map { name ->
            argsByOldName[name] ?: defaults[name] ?: "TODO()"
        }

        val newArgText = newArgs.joinToString(", ")
        edits.add(TextEdit(argList.textOffset + 1, argList.textLength - 2, newArgText))
    }

    private fun rewriteNamedCallSite(
        argList: KtValueArgumentList,
        args: List<KtValueArgument>,
        oldParamNames: List<String>,
        newParamNames: List<String>,
        defaults: Map<String, String>,
        edits: MutableList<TextEdit>,
    ) {
        // Extract existing named args
        val namedArgs = mutableMapOf<String, String>()
        for (arg in args) {
            val name = arg.getArgumentName()?.text ?: continue
            val value = arg.getArgumentExpression()?.text ?: ""
            namedArgs[name] = value
        }

        // Build new arg list: keep existing named args that are still in params, add new ones
        val newArgs = newParamNames.mapNotNull { name ->
            val value = namedArgs[name] ?: defaults[name] ?: return@mapNotNull null
            "$name = $value"
        }

        val newArgText = newArgs.joinToString(", ")
        edits.add(TextEdit(argList.textOffset + 1, argList.textLength - 2, newArgText))
    }

    internal fun parseParamList(params: String): List<ParamSpec> {
        if (params.isBlank()) return emptyList()
        return params.split(",").map { param ->
            val trimmed = param.trim()
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx < 0) error("Invalid param spec: $trimmed (expected 'name: Type')")
            val name = trimmed.substring(0, colonIdx).trim()
            val typeAndDefault = trimmed.substring(colonIdx + 1).trim()
            ParamSpec(name, typeAndDefault)
        }
    }

    private fun applyEdits(content: String, edits: List<TextEdit>): String {
        var result = content
        for (edit in edits.sortedByDescending { it.offset }) {
            result = result.substring(0, edit.offset) + edit.replacement + result.substring(edit.offset + edit.length)
        }
        return result
    }
}
