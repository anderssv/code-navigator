package no.f12.codenavigator.navigation.testalignment

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class TestAlignmentTaskConfig(
    val exclude: Regex?,
    val format: OutputFormat,
    val failOnViolation: Boolean,
    val maxViolations: Int,
) {
    fun toAnalysisConfig(): TestAlignmentAnalysisConfig = TestAlignmentAnalysisConfig(exclude = exclude)

    companion object {
        fun parse(properties: Map<String, String?>): TestAlignmentTaskConfig = TestAlignmentTaskConfig(
            exclude = TaskRegistry.EXCLUDE.parseFrom(properties)?.let { Regex(it) },
            format = ParamDef.parseFormat(properties),
            failOnViolation = TaskRegistry.FAIL_ON_VIOLATION.parseFrom(properties),
            maxViolations = TaskRegistry.MAX_VIOLATIONS.parseFrom(properties),
        )
    }
}
