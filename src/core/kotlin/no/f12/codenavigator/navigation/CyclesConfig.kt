package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat

data class CyclesConfig(
    val rootPackage: String,
    val depth: Int,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): CyclesConfig = CyclesConfig(
            rootPackage = properties["root-package"] ?: "",
            depth = properties["depth"]?.toIntOrNull() ?: 2,
            format = OutputFormat.from(
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
            ),
        )
    }
}
