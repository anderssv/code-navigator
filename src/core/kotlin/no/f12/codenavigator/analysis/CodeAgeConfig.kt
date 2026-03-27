package no.f12.codenavigator.analysis

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import java.time.LocalDate

data class CodeAgeConfig(
    val after: LocalDate,
    val top: Int,
    val followRenames: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): CodeAgeConfig = CodeAgeConfig(
            after = TaskRegistry.AFTER.parse(properties["after"]),
            top = TaskRegistry.TOP.parse(properties["top"]),
            followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
