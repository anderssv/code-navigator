package no.f12.codenavigator.navigation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.config.OutputFormat

data class FindUsagesConfig(
    val ownerClass: String?,
    val method: String?,
    val field: String?,
    val type: String?,
    val outsidePackage: String?,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FindUsagesConfig {
            val ownerClass = properties["ownerClass"]
            val type = properties["type"]
            val method = properties["method"]
            val field = properties["field"]
            if (ownerClass == null && type == null) {
                throw IllegalArgumentException(
                    "Missing required property. Provide either 'ownerClass' or 'type'.",
                )
            }
            if (field != null && method != null) {
                throw IllegalArgumentException(
                    "Cannot specify both 'field' and 'method'. Use 'field' for property/field usages, 'method' for method call usages.",
                )
            }
            if (field != null && ownerClass == null) {
                throw IllegalArgumentException(
                    "The 'field' parameter requires 'ownerClass' to identify which class owns the field.",
                )
            }
            return FindUsagesConfig(
                ownerClass = ownerClass,
                method = method,
                field = field,
                type = type,
                outsidePackage = properties["outside-package"],
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
