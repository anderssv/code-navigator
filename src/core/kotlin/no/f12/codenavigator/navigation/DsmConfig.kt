package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat

data class DsmConfig(
    val rootPackage: String,
    val depth: Int,
    val htmlPath: String?,
    val format: OutputFormat,
    val cyclesOnly: Boolean,
    val cycleFilter: Pair<String, String>?,
) {
    companion object {
        fun parse(properties: Map<String, String?>): DsmConfig = DsmConfig(
            rootPackage = properties["root-package"] ?: "",
            depth = properties["dsm-depth"]?.toIntOrNull() ?: 2,
            htmlPath = properties["dsm-html"],
            format = OutputFormat.from(
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
            ),
            cyclesOnly = properties["cycles"]?.toBoolean() ?: false,
            cycleFilter = parseCycleFilter(properties["cycle"]),
        )

        fun parseCycleFilter(value: String?): Pair<String, String>? {
            if (value == null) return null
            val parts = value.split(",").map { it.trim() }
            if (parts.size != 2 || parts.any { it.isBlank() }) return null
            return parts[0] to parts[1]
        }
    }
}
