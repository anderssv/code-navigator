package no.f12.codenavigator.analysis

import no.f12.codenavigator.ParamDef
import no.f12.codenavigator.TaskRegistry
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
            after = TaskRegistry.AFTER.parse(properties["after"]),
            minSharedRevs = TaskRegistry.MIN_SHARED_REVS.parse(properties["min-shared-revs"]),
            minCoupling = TaskRegistry.MIN_COUPLING.parse(properties["min-coupling"]),
            maxChangesetSize = TaskRegistry.MAX_CHANGESET_SIZE.parse(properties["max-changeset-size"]),
            followRenames = !TaskRegistry.NO_FOLLOW.parseFrom(properties),
            top = TaskRegistry.TOP.parse(properties["top"]),
            format = ParamDef.parseFormat(properties),
        )
    }
}
