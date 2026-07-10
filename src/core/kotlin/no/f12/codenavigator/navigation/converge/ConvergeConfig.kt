package no.f12.codenavigator.navigation.converge

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import java.time.LocalDate

data class ConvergeConfig(
    val mode: ConvergeMode,
    val packageFilter: PackageName?,
    val exclude: Regex?,
    val after: LocalDate,
    val minSharedRevs: Int,
    val minCoupling: Int,
    val maxChangesetSize: Int,
    val followRenames: Boolean,
    val top: Int,
    val scope: Scope,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ConvergeConfig = ConvergeConfig(
            mode = ConvergeMode.parse(TaskRegistry.CONVERGE_MODE.parseFrom(properties)),
            packageFilter = TaskRegistry.PACKAGE_FILTER.parseFrom(properties)?.let { PackageName(it) },
            exclude = TaskRegistry.CONVERGE_EXCLUDE.parseFrom(properties)?.let { Regex(it, RegexOption.IGNORE_CASE) },
            after = TaskRegistry.AFTER.parseFrom(properties),
            minSharedRevs = TaskRegistry.MIN_SHARED_REVS.parseFrom(properties),
            minCoupling = TaskRegistry.MIN_COUPLING.parseFrom(properties),
            maxChangesetSize = TaskRegistry.MAX_CHANGESET_SIZE.parseFrom(properties),
            followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            // Defaults to prod: test-only wiring (e.g. a shared test context reaching into every feature
            // package) routinely creates cycles/coupling that don't reflect real production architecture.
            scope = Scope.parse(TaskRegistry.SCOPE.parseFrom(properties) ?: "prod"),
            format = ParamDef.parseFormat(properties),
        )
    }
}
