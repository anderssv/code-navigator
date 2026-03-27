package no.f12.codenavigator.navigation

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.config.OutputFormat

data class ListClassesConfig(
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ListClassesConfig = ListClassesConfig(
            format = ParamDef.parseFormat(properties),
        )
    }
}
