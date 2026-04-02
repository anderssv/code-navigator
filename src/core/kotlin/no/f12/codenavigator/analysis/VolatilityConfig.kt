package no.f12.codenavigator.analysis

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import java.time.LocalDate

data class VolatilityConfig(
    val after: LocalDate,
    val top: Int,
    val minRevs: Int,
    val followRenames: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): VolatilityConfig = VolatilityConfig(
            after = TaskRegistry.AFTER.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            minRevs = TaskRegistry.MIN_REVS.parseFrom(properties),
            followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
