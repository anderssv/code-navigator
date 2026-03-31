package no.f12.codenavigator.navigation.changedsince

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class ChangedSinceConfig(
    val ref: String?,
    val projectOnly: Boolean,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ChangedSinceConfig =
            ChangedSinceConfig(
                ref = TaskRegistry.REF.parseFrom(properties),
                projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
                prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
                testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
                format = ParamDef.parseFormat(properties),
            )
    }
}
