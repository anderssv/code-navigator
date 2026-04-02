package no.f12.codenavigator.navigation.interfaces

import no.f12.codenavigator.navigation.core.ClassName

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
}
