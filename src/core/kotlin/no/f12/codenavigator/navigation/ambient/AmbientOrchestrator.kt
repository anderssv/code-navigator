package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.RingDiagnosis
import no.f12.codenavigator.navigation.dsm.RingsAnalysis
import no.f12.codenavigator.navigation.dsm.RingsOrchestrator
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class AmbientOutput(
    val result: AmbientResult?,
    val skippedFileWarning: String?,
    /**
     * True when no `--domain` was given and `cnavRings` could not produce a layered hexagon, so
     * there is no ring 0 to check. Reported rather than silently analysing every class — "every
     * class is domain" would turn this into an unfocused lint run over the whole project.
     */
    val subjectsUnavailable: Boolean = false,
)

/** Shared by AmbientTask (Gradle) and AmbientMojo (Maven) so both build tools run the exact same pipeline. */
object AmbientOrchestrator {

    fun run(
        config: AmbientTaskConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        reportFile: File,
        projectDir: File,
    ): AmbientOutput {
        val scopedDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = scopedDirs.map { it.first }

        val scanResult = AmbientCallScanner.scan(classDirectories)
        val skippedFileWarning = SkippedFileReporter.report(scanResult.skippedFiles, reportFile)

        val subjects = resolveSubjects(config, classDirectories, taggedDirs, projectDir, reportFile)
            ?: return AmbientOutput(result = null, skippedFileWarning = skippedFileWarning, subjectsUnavailable = true)

        val result = AmbientBuilder.analyze(
            scanResult.data,
            AmbientAnalysisConfig(
                domainClasses = subjects.classes,
                categories = config.categories,
                exclude = config.exclude,
                subjectSource = subjects.source,
            ),
        )

        return AmbientOutput(result = result, skippedFileWarning = skippedFileWarning)
    }

    private data class Subjects(val classes: Set<ClassName>, val source: SubjectSource)

    private fun resolveSubjects(
        config: AmbientTaskConfig,
        classDirectories: List<File>,
        taggedDirs: List<Pair<File, SourceSet>>,
        projectDir: File,
        reportFile: File,
    ): Subjects? {
        val domain = config.domain
        if (domain != null) {
            val matching = scanProjectClasses(classDirectories).filter { domain.containsMatchIn(it.value) }.toSet()
            return Subjects(matching, SubjectSource.DOMAIN_PATTERN)
        }

        val analysis = RingsOrchestrator.run(
            taggedDirs,
            Scope.PROD,
            bootstrap = false,
            plan = emptyList(),
            projectDir = projectDir,
            reportFile = reportFile,
        )
        val layering = (analysis as? RingsAnalysis.Hexagonal)?.output?.layering ?: return null
        if (layering.diagnosis != RingDiagnosis.LAYERED) return null

        val ringZero = layering.rings.filterValues { it == 0 }.keys.map { it.topLevelClass() }.toSet()
        return Subjects(ringZero, SubjectSource.RINGS)
    }
}
