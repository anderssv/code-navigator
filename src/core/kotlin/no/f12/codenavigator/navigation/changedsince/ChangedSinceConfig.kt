package no.f12.codenavigator.navigation.changedsince

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class ChangedSinceConfig(
    val ref: String?,
    val projectOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ChangedSinceConfig =
            ChangedSinceConfig(
                ref = properties["ref"],
                projectOnly = TaskRegistry.PROJECTONLY_ON.parse(properties["project-only"]),
                format = ParamDef.parseFormat(properties),
            )
    }
}
