package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.TaskRegistry

fun parseTop(properties: Map<String, String?>): Int {
    val raw = properties["top"]
    if (raw.equals("unlimited", ignoreCase = true)) return Int.MAX_VALUE
    val top = TaskRegistry.TOP.parseFrom(properties)
    require(top >= 1) { "top must be >= 1. Omit the top parameter for unlimited results, or use top=unlimited." }
    return top
}
