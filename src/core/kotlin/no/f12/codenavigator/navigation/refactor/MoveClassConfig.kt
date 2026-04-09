package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class MoveClassConfig(
    val from: String,
    val to: String,
    val preview: Boolean,
    val format: OutputFormat,
) {
    val fromPackage: String get() = from.substringBeforeLast(".")
    val fromSimpleName: String get() = from.substringAfterLast(".")
    val toPackage: String get() = to.substringBeforeLast(".")
    val toSimpleName: String get() = to.substringAfterLast(".")

    companion object {
        fun parse(properties: Map<String, String?>): MoveClassConfig {
            val from = TaskRegistry.MOVE_FROM.parseRequiredFrom(properties)
            val to = TaskRegistry.MOVE_TO.parseRequiredFrom(properties)
            val preview: Boolean = TaskRegistry.PREVIEW.parseFrom(properties)
            return MoveClassConfig(
                from = from,
                to = to,
                preview = preview,
                format = ParamDef.parseFormat(properties),
            )
        }
    }
}
