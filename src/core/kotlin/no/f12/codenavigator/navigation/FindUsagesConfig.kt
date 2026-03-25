package no.f12.codenavigator.navigation

import no.f12.codenavigator.OutputFormat

data class FindUsagesConfig(
    val ownerClass: String?,
    val method: String?,
    val type: String?,
    val outsidePackage: String?,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindUsagesConfig {
            val ownerClass = properties["ownerClass"]
            val type = properties["type"]
            if (ownerClass == null && type == null) {
                throw IllegalArgumentException(
                    "Missing required property. Provide either 'ownerClass' or 'type'.",
                )
            }
            return FindUsagesConfig(
                ownerClass = ownerClass,
                method = properties["method"],
                type = type,
                outsidePackage = properties["outside-package"],
                format = OutputFormat.from(
                    format = properties["format"],
                    llm = properties["llm"]?.toBoolean(),
                ),
            )
        }
    }
}
