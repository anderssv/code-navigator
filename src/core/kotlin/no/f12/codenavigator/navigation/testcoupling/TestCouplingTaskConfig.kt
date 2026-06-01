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
            )
        }
    }
}
