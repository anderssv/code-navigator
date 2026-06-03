package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.SimulateMoveAnalyzer
import no.f12.codenavigator.navigation.dsm.SimulateMoveConfig
import no.f12.codenavigator.navigation.dsm.SimulateMoveFormatter
import no.f12.codenavigator.navigation.types.ClassName
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class SimulateMoveTask : CodeNavigatorTask() {

    @Option(option = "type", description = "Class to simulate moving (simple or fully qualified name)")
    @get:Internal
    var type: String? = null

    @Option(option = "to-package", description = "Target package (dot-separated)")
    @get:Internal
    var toPackage: String? = null

    @Option(option = "package-filter", description = "Only include packages under this prefix")
    @get:Internal
    var packageFilter: String? = null

    @Option(option = "dsm-depth", description = "Package grouping depth")
    @get:Internal
    var dsmDepth: String? = null

    @Option(option = "root-package", description = "Deprecated: use package-filter instead")
    @get:Internal
    var rootPackage: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        type?.let { put("type", it) }
        toPackage?.let { put("to-package", it) }
        packageFilter?.let { put("package-filter", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        rootPackage?.let { put("root-package", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun simulateMove() {
        val extension = project.codeNavigatorExtension()
        val rawOptionsMap = buildOptionsMap()
        val cliProps = TaskRegistry.SIMULATE_MOVE.enhanceProperties(rawOptionsMap)
        val props = extension.resolveProperties(cliProps)

        val config = SimulateMoveConfig.parse(props)

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        val projectClasses = scanProjectClasses(classDirectories)

        // Use raw type value (not pattern-enhanced) for exact class resolution
        val rawType = rawOptionsMap["type"] ?: config.type
        val resolvedClass = resolveClassName(rawType, projectClasses)
            ?: run {
                logger.lifecycle(OutputWrapper.emptyResult(config.format, "Class '${config.type}' not found in project."))
                return
            }

        val result = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, includeExternal = false, filterTargets = true)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val dependencies = result.data

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())

        val moveResult = SimulateMoveAnalyzer.analyze(
            dependencies = dependencies,
            classToMove = resolvedClass,
            targetPackage = config.toPackage,
        )

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> SimulateMoveFormatter.formatText(moveResult, displayPrefix)
                OutputFormat.JSON -> SimulateMoveFormatter.formatText(moveResult, displayPrefix)
                OutputFormat.LLM -> SimulateMoveFormatter.formatLlm(moveResult)
            }
        })
    }

    private fun resolveClassName(name: String, projectClasses: Set<ClassName>): ClassName? {
        // Exact match first
        val exact = ClassName(name)
        if (exact in projectClasses) return exact

        // Simple name match (case-sensitive)
        val matches = projectClasses.filter { it.simpleName() == name }
        return when {
            matches.size == 1 -> matches.first()
            matches.size > 1 -> {
                logger.warn("Ambiguous class name '$name'. Matches: ${matches.joinToString(", ")}. Using first match.")
                matches.first()
            }
            else -> null
        }
    }
}
