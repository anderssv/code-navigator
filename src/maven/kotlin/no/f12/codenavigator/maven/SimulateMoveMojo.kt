package no.f12.codenavigator.maven

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
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "simulate-move")
@Execute(phase = LifecyclePhase.COMPILE)
class SimulateMoveMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "type")
    private var type: String? = null

    @Parameter(property = "to-package")
    private var toPackage: String? = null

    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "dsm-depth")
    private var depth: String? = null

    @Parameter(property = "root-package")
    private var rootPackage: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "plan-file")
    private var planFile: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = SimulateMoveConfig.parse(TaskRegistry.SIMULATE_MOVE.enhanceProperties(buildPropertyMap()))

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val projectClasses = scanProjectClasses(classDirectories)

        val resolvedClass = resolveClassName(config.type, projectClasses)
            ?: run {
                println(OutputWrapper.emptyResult(config.format, "Class '${config.type}' not found in project."))
                return
            }

        val result = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, includeExternal = false, filterTargets = true)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val dependencies = result.data

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())

        val moveResult = SimulateMoveAnalyzer.analyze(
            dependencies = dependencies,
            classToMove = resolvedClass,
            targetPackage = config.toPackage,
        )

        println(OutputWrapper.formatAndWrap(config.format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> SimulateMoveFormatter.formatText(moveResult, displayPrefix)
                OutputFormat.JSON -> SimulateMoveFormatter.formatText(moveResult, displayPrefix)
                OutputFormat.LLM -> SimulateMoveFormatter.formatLlm(moveResult)
            }
        })
    }

    private fun resolveClassName(name: String, projectClasses: Set<ClassName>): ClassName? {
        val exact = ClassName(name)
        if (exact in projectClasses) return exact
        val matches = projectClasses.filter { it.simpleName() == name }
        return when {
            matches.size == 1 -> matches.first()
            matches.size > 1 -> {
                log.warn("Ambiguous class name '$name'. Matches: ${matches.joinToString(", ")}. Using first match.")
                matches.first()
            }
            else -> null
        }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        type?.let { put("type", it) }
        toPackage?.let { put("to-package", it) }
        packageFilter?.let { put("package-filter", it) }
        depth?.let { put("dsm-depth", it) }
        rootPackage?.let { put("root-package", it) }
        scope?.let { put("scope", it) }
        planFile?.let { put("plan-file", it) }
    }
}
