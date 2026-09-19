package no.f12.codenavigator.navigation.testalignment

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class TestAlignmentOutput(
    val result: TestAlignmentResult?,
    val skippedFileWarning: String?,
    /** True when the project has no compiled test classes at all — nothing to check, not a clean bill. */
    val noTestClasses: Boolean = false,
)

/** Shared by TestAlignmentTask (Gradle) and TestAlignmentMojo (Maven) so both run the exact same pipeline. */
object TestAlignmentOrchestrator {

    fun run(
        config: TestAlignmentTaskConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        cacheDir: File,
        reportFile: File,
    ): TestAlignmentOutput {
        // Both source sets are required by construction: the check compares where tests live against
        // where their production subjects live, so it never takes a --scope.
        if (taggedDirs.none { it.second == SourceSet.TEST && it.first.exists() }) {
            return TestAlignmentOutput(result = null, skippedFileWarning = null, noTestClasses = true)
        }

        val graphResult = CallGraphCache.getOrBuildTagged(File(cacheDir, "call-graph.cache"), taggedDirs)
        val skippedFileWarning = SkippedFileReporter.report(graphResult.skippedFiles, reportFile)

        val result = TestAlignmentBuilder.analyze(graphResult.data, config.toAnalysisConfig())

        return TestAlignmentOutput(result = result, skippedFileWarning = skippedFileWarning)
    }
}
