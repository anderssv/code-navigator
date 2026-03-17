package no.f12.codenavigator

object InterfaceFormatter {

    fun format(registry: InterfaceRegistry, interfaceNames: List<String>): String = buildString {
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
