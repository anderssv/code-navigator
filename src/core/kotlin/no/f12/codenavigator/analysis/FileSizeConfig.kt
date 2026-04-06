package no.f12.codenavigator.analysis

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class FileSizeConfig(
    val over: Int,
    val top: Int,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): FileSizeConfig = FileSizeConfig(
            over = TaskRegistry.OVER.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
