package no.f12.codenavigator.navigation.report

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.dsm.CohesionConfig
import no.f12.codenavigator.navigation.dsm.MoveSuggestConfig
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry

/**
 * Bundles the parsed inputs for the composite `cnavReport` pipeline. The sub-analyses each have their
 * own config parsed from the same property map, so `cnavReport` reuses those parsers rather than
 * re-deriving their settings.
 */
data class ReportConfig(
    val scope: Scope,
    val packageFilter: PackageName?,
    val top: Int,
    val deadCode: DeadCodeConfig,
    val moveSuggest: MoveSuggestConfig,
    val cohesion: CohesionConfig,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ReportConfig = ReportConfig(
            scope = Scope.parse(properties["scope"]),
            packageFilter = properties["package-filter"]?.let { PackageName(it) },
            top = properties["top"]?.toIntOrNull() ?: 20,
            deadCode = DeadCodeConfig.parse(properties),
            moveSuggest = MoveSuggestConfig.parse(properties),
            cohesion = CohesionConfig.parse(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
