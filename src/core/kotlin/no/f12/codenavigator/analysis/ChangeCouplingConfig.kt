package no.f12.codenavigator.analysis

import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.config.OutputFormat
import java.time.LocalDate

data class ChangeCouplingConfig(
    val after: LocalDate,
    val minSharedRevs: Int,
    val minCoupling: Int,
    val maxChangesetSize: Int,
    val followRenames: Boolean,
    val top: Int,
    val format: OutputFormat,
) {
    companion object {
        fun parse(properties: Map<String, String?>): ChangeCouplingConfig = ChangeCouplingConfig(
            after = TaskRegistry.AFTER.parseFrom(properties),
            minSharedRevs = TaskRegistry.MIN_SHARED_REVS.parseFrom(properties),
            minCoupling = TaskRegistry.MIN_COUPLING.parseFrom(properties),
            maxChangesetSize = TaskRegistry.MAX_CHANGESET_SIZE.parseFrom(properties),
            followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
            top = TaskRegistry.TOP.parseFrom(properties),
            format = ParamDef.parseFormat(properties),
        )
    }
}
