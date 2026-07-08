package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

data class ClassMetricsConfig(
    val minMethods: Int,
    val minTcc: Double,
    val maxWmc: Int,
    val maxCbo: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ClassMetricsConfig = ClassMetricsConfig(
            minMethods = TaskRegistry.MIN_METHODS.parseFrom(properties),
            minTcc = TaskRegistry.MIN_TCC.parseFrom(properties),
            maxWmc = TaskRegistry.MAX_WMC.parseFrom(properties),
            maxCbo = TaskRegistry.MAX_CBO.parseFrom(properties),
            scope = Scope.parse(properties["scope"]),
            format = ParamDef.parseFormat(properties),
        )
    }
}
