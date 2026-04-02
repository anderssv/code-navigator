package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.TaskRegistry

fun parseTop(properties: Map<String, String?>, defaultValue: Int = TaskRegistry.TOP.parse(null)): Int {
    val raw = properties["top"] ?: return defaultValue
    val top = raw.toIntOrNull()
        ?: throw IllegalArgumentException("top must be a number, got '$raw'.")
    require(top >= 1) { "top must be >= 1. Omit the top parameter to show all results." }
    return top
}
