package no.f12.codenavigator.navigation.context

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
}
