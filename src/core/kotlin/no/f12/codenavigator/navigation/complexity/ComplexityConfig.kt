package no.f12.codenavigator.navigation.complexity

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.config.OutputFormat

data class ComplexityConfig(
    val classPattern: String,
    val projectOnly: Boolean,
    val detail: Boolean,
    val collapseLambdas: Boolean,
    val top: Int,
    val prodOnly: Boolean,
    val testOnly: Boolean,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ComplexityConfig = ComplexityConfig(
            classPattern = properties["pattern"] ?: ".*",
            projectOnly = TaskRegistry.PROJECTONLY.parseFrom(properties),
            detail = TaskRegistry.DETAIL.parseFrom(properties),
            collapseLambdas = TaskRegistry.COLLAPSE_LAMBDAS.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            prodOnly = TaskRegistry.PROD_ONLY.parseFrom(properties),
            testOnly = TaskRegistry.TEST_ONLY.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
