package no.f12.codenavigator.analysis

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import java.time.LocalDate

data class AuthorAnalysisConfig(
    val after: LocalDate,
    val minRevs: Int,
    val top: Int,
    val followRenames: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): AuthorAnalysisConfig = AuthorAnalysisConfig(
            after = TaskRegistry.AFTER.parse(properties["after"]),
            minRevs = TaskRegistry.MIN_REVS.parse(properties["min-revs"]),
            top = TaskRegistry.TOP.parse(properties["top"]),
            followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
