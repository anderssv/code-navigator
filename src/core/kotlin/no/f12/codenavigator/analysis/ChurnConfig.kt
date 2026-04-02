package no.f12.codenavigator.analysis

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import java.time.LocalDate

data class ChurnConfig(
    val after: LocalDate,
    val top: Int,
    val followRenames: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ChurnConfig = ChurnConfig(
            after = TaskRegistry.AFTER.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
