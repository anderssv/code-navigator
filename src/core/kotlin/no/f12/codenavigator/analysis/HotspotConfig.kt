package no.f12.codenavigator.analysis

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import java.time.LocalDate

data class HotspotConfig(
    val after: LocalDate,
    val minRevs: Int,
    val top: Int,
    val followRenames: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): HotspotConfig = HotspotConfig(
            after = TaskRegistry.AFTER.parseFrom(properties),
            minRevs = TaskRegistry.MIN_REVS.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
