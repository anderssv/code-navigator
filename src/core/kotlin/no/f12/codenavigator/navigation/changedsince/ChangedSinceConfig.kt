package no.f12.codenavigator.navigation.changedsince

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.Scope

data class ChangedSinceConfig(
    val ref: String?,
    val projectOnly: Boolean,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ChangedSinceConfig =
            ChangedSinceConfig(
                ref = TaskRegistry.REF.parseFrom(properties),
                projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
            )
    }
}
