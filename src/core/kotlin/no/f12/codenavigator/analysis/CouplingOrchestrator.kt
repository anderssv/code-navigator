package no.f12.codenavigator.analysis

import java.io.File

/** Shared by ChangeCouplingTask (Gradle) and ChangeCouplingMojo (Maven) so both build tools run the exact same pipeline. */
object CouplingOrchestrator {

    fun run(config: ChangeCouplingConfig, projectDir: File): List<CoupledPair> {
        val commits = GitLogRunner.run(projectDir, config.after, followRenames = config.followRenames)
        val pairs = ChangeCouplingBuilder.build(commits, config.minSharedRevs, config.minCoupling, config.maxChangesetSize, config.top)
        return StalePairMarker.mark(pairs, projectDir)
    }
}
