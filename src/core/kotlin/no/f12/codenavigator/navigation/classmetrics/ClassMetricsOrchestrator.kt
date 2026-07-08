package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import java.io.File

data class ClassMetricsOutput(
    val results: List<ClassMetricsResult>,
    val skippedFileWarning: String?,
)

/** Shared by ClassMetricsTask (Gradle) and ClassMetricsMojo (Maven) so both build tools run the exact same pipeline. */
object ClassMetricsOrchestrator {

    fun run(config: ClassMetricsConfig, classDirectories: List<File>, reportFile: File): ClassMetricsOutput {
        val scanResult = ClassMetricsAnalyzer.analyze(classDirectories)
        val skippedFileWarning = SkippedFileReporter.report(scanResult.skippedFiles, reportFile)

        val filtered = scanResult.data
            .filter { it.totalMethods >= config.minMethods }
            .filter { it.tcc >= config.minTcc }
            .filter { it.wmc <= config.maxWmc }
            .filter { it.cbo <= config.maxCbo }

        return ClassMetricsOutput(filtered, skippedFileWarning)
    }
}
