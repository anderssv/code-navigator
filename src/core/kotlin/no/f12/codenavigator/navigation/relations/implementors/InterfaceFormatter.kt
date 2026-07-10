package no.f12.codenavigator.navigation.relations.implementors

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.navigation.types.ClassName

object InterfaceFormatter {

    fun format(registry: InterfaceRegistry, interfaceNames: List<ClassName>): String = buildString {
        interfaceNames.forEachIndexed { index, ifaceName ->
            if (index > 0) appendLine()
            val implementors = registry.implementorsOf(ifaceName)
            appendLine("=== $ifaceName (${implementors.size} implementors) ===")
            implementors.forEach { impl ->
                appendLine("  ${impl.className} (${impl.sourceFile})")
            }
        }
    }.trimEnd()

    fun formatJson(registry: InterfaceRegistry, interfaceNames: List<ClassName>): String =
        jsonArray(interfaceNames.sorted()) { name ->
            val implementors = registry.implementorsOf(name)
            jsonObject(
                "interface" to name.toString(),
                "implementors" to JsonRaw(jsonArray(implementors.sortedBy { it.className }) { impl ->
                    jsonObject("className" to impl.className.toString(), "sourceFile" to impl.sourceFile)
                }),
            )
        }

    fun formatLlm(registry: InterfaceRegistry, interfaceNames: List<ClassName>): String =
        interfaceNames.sorted().joinToString("\n") { name ->
            val impls = registry.implementorsOf(name).sortedBy { it.className }
            "$name: ${impls.joinToString(",") { "${it.className}(${it.sourceFile})" }}"
        }
}
