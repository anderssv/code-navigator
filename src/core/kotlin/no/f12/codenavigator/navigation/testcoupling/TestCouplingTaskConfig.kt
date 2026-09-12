package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope

data class TestCouplingTaskConfig(
    val ports: Regex,
    val detail: Boolean,
    val exclude: Regex?,
    val scope: Scope,
    val format: OutputFormat,
    val subjects: Set<CouplingSubjectKind>,
    val writeMethods: Set<String>,
    val readMethods: Set<String>,
    val failOnViolation: Boolean,
    val maxViolations: Int,
) {
    companion object {
        fun parse(properties: Map<String, String?>): TestCouplingTaskConfig {
            val portsString = TaskRegistry.PORTS.parseFrom(properties)
                ?: error("Required parameter 'ports' not specified. Set --ports to a regex matching your port interface names (e.g. \".*Repository|.*Client\").")
            val excludeString = TaskRegistry.EXCLUDE.parseFrom(properties)
            return TestCouplingTaskConfig(
                ports = Regex(portsString),
                detail = TaskRegistry.DETAIL.parseFrom(properties) ?: false,
                exclude = excludeString?.let { Regex(it) },
                scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties)),
                format = ParamDef.parseFormat(properties),
                subjects = parseSubjects(TaskRegistry.COUPLING_SUBJECT.parseFrom(properties)),
                writeMethods = TaskRegistry.WRITE_METHODS.parseFrom(properties).toSet(),
                readMethods = TaskRegistry.READ_METHODS.parseFrom(properties).toSet(),
                failOnViolation = TaskRegistry.FAIL_ON_VIOLATION.parseFrom(properties),
                maxViolations = TaskRegistry.MAX_VIOLATIONS.parseFrom(properties),
            )
        }

        private fun parseSubjects(value: String?): Set<CouplingSubjectKind> = when (value?.lowercase()) {
            null, "tests", "test" -> setOf(CouplingSubjectKind.TEST)
            "adapters", "adapter" -> setOf(CouplingSubjectKind.ADAPTER)
            "both" -> setOf(CouplingSubjectKind.TEST, CouplingSubjectKind.ADAPTER)
            else -> error("Invalid --subject '$value'. Must be one of: tests, adapters, both.")
        }
    }
}

