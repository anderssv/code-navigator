package no.f12.codenavigator.analysis

import java.io.File

/** Shared by PackageVolatilityTask (Gradle) and PackageVolatilityMojo (Maven) so both build tools run the exact same pipeline. */
object VolatilityOrchestrator {

    fun run(config: VolatilityConfig, projectDir: File): PackageVolatilityResult {
        val commits = GitLogRunner.run(projectDir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, config.minRevs)
        return PackageVolatilityBuilder.build(hotspots, config.top)
    }
}
