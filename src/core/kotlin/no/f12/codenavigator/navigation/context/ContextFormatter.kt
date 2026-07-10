package no.f12.codenavigator.navigation.context

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.classinfo.ClassDetailFormatter

object ContextFormatter {

    fun format(result: ContextResult): String = buildString {
        append(ClassDetailFormatter.format(listOf(result.classDetail)))

        if (result.callers.isNotEmpty()) {
            appendLine()
            appendLine()
            appendLine("Callers")
            appendLine("-------")
            append(CallTreeFormatter.renderTrees(result.callers, CallDirection.CALLERS))
        }

        if (result.callees.isNotEmpty()) {
            appendLine()
            appendLine()
            appendLine("Callees")
            appendLine("-------")
            append(CallTreeFormatter.renderTrees(result.callees, CallDirection.CALLEES))
        }

        if (result.implementors.isNotEmpty()) {
            appendLine()
            appendLine()
            appendLine("Implementors")
            appendLine("------------")
            result.implementors.forEach { impl ->
                appendLine("  ${impl.className} (${impl.sourceFile})")
            }
        }

        if (result.implementedInterfaces.isNotEmpty()) {
            appendLine()
            appendLine()
            appendLine("Implements")
            appendLine("----------")
            result.implementedInterfaces.forEach { iface ->
                appendLine("  $iface")
            }
        }
    }.trimEnd()

    fun formatJson(result: ContextResult): String =
        jsonObject(
            "classDetail" to JsonRaw(ClassDetailFormatter.formatJson(listOf(result.classDetail))),
            "callers" to JsonRaw(CallTreeFormatter.formatJson(result.callers, CallDirection.CALLERS)),
            "callees" to JsonRaw(CallTreeFormatter.formatJson(result.callees, CallDirection.CALLEES)),
            "implementors" to JsonRaw(jsonArray(result.implementors) { impl ->
                jsonObject("className" to impl.className.toString(), "sourceFile" to impl.sourceFile)
            }),
            "implementedInterfaces" to JsonRaw(jsonStringArray(result.implementedInterfaces.map { it.toString() })),
        )

    fun formatLlm(result: ContextResult): String = buildString {
        append(ClassDetailFormatter.formatLlm(listOf(result.classDetail)))
        if (result.callers.isNotEmpty()) {
            appendLine()
            appendLine("callers:")
            append(CallTreeFormatter.formatLlm(result.callers, CallDirection.CALLERS))
        }
        if (result.callees.isNotEmpty()) {
            appendLine()
            appendLine("callees:")
            append(CallTreeFormatter.formatLlm(result.callees, CallDirection.CALLEES))
        }
        if (result.implementors.isNotEmpty()) {
            appendLine()
            append("implementors:${result.implementors.joinToString(",") { "${it.className}(${it.sourceFile})" }}")
        }
        if (result.implementedInterfaces.isNotEmpty()) {
            appendLine()
            append("implements:${result.implementedInterfaces.joinToString(",")}")
        }
    }.trimEnd()
}
