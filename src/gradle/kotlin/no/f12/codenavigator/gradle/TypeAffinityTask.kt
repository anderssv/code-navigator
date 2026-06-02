package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.TypeAffinityBuilder
import no.f12.codenavigator.navigation.dsm.TypeAffinityConfig
import no.f12.codenavigator.navigation.dsm.TypeAffinityFormatter
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class TypeAffinityTask : CodeNavigatorTask() {

    @Option(option = "package", description = "Package to analyze for type affinity (required)")
    @get:Internal
    var pkg: String? = null

    @Option(option = "threshold", description = "Max number of consumer domains for single-owner (default: 1)")
    @get:Internal
    var threshold: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pkg?.let { put("package", it) }
        threshold?.let { put("threshold", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun analyzeAffinity() {
        val extension = project.codeNavigatorExtension()
        val props = extension.resolveProperties(TaskRegistry.TYPE_AFFINITY.enhanceProperties(buildOptionsMap()))

        val config = TypeAffinityConfig.parse(props)
        val scopeVal = Scope.parse(props["scope"])

        val classDirectories = project.taggedClassDirectories()
            .filter { scopeVal.matchesSourceSet(it.second) }
            .map { it.first }
        val projectClasses = scanProjectClasses(classDirectories)

        val extractResult = DsmDependencyExtractor.extract(classDirectories, projectClasses, packageFilter = null, includeExternal = false, filterTargets = true)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { logger.warn(it) }

        val result = TypeAffinityBuilder.analyze(extractResult.data, config.targetPackage, config.threshold)

        if (result.singleOwnerTypes.isEmpty() && result.sharedTypes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No types found in package '${config.targetPackage}' with external consumers."))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
            TypeAffinityFormatter.format(result, format)
        })
    }
}
