package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.bytecode.RootPackageDetector
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.CycleDetector
import no.f12.codenavigator.navigation.dsm.CyclesConfig
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.dsm.PlanMutator
import no.f12.codenavigator.navigation.dsm.TestInvolvement
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.types.Scope
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "cycles")
@Execute(phase = LifecyclePhase.COMPILE)
class CyclesMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "root-package")
    private var rootPackage: String? = null

    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "include-external")
    private var includeExternal: String? = null

    @Parameter(property = "dsm-depth")
    private var depth: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "plan-file")
    private var planFile: String? = null

    @Parameter(property = "fail-on-violation")
    private var failOnViolation: String? = null

    @Parameter(property = "max-cycles")
    private var maxCycles: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = CyclesConfig.parse(TaskRegistry.CYCLE_DETECTION.enhanceProperties(buildPropertyMap()))
        config.deprecations().forEach { log.warn(it) }

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val projectClasses = scanProjectClasses(classDirectories)

        val result = DsmDependencyExtractor.extract(classDirectories, projectClasses, config.packageFilter, config.includeExternal, filterTargets = true)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val dependencies = applyPlanFile(result.data, planFile, log)

        val displayPrefix = RootPackageDetector.detectFromClassNames(projectClasses.toList())
        val matrix = DsmMatrixBuilder.build(dependencies, displayPrefix, config.depth)

        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)
        val details = CycleDetector.enrich(cycles, matrix)

        if (details.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No package cycles detected."))
            return
        }

        val testNotice = if (config.scope == Scope.ALL) {
            val resolver = SourceSetResolver.from(taggedDirs)
            val classEdges = details.flatMap { it.edges }.flatMap { it.classEdges }
            TestInvolvement.notice(
                TestInvolvement.count(classEdges) { resolver.sourceSetOf(it) },
                "cycle edges",
            )
        } else {
            null
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> CyclesFormatter.format(details, displayPrefix = displayPrefix).let { if (testNotice != null) "$it\n\n$testNotice" else it }
        OutputFormat.JSON -> JsonFormatter.formatCycles(details, displayPrefix = displayPrefix)
        OutputFormat.LLM -> LlmFormatter.formatCycles(details, displayPrefix = displayPrefix).let { if (testNotice != null) "$it\n\n$testNotice" else it }
    }
})

        if (config.failOnViolation && details.size > config.maxCycles) {
            throw MojoFailureException("cnav:cycles found ${details.size} cycle(s), exceeding --max-cycles=${config.maxCycles}")
        }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        rootPackage?.let { put("root-package", it) }
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        depth?.let { put("dsm-depth", it) }
        scope?.let { put("scope", it) }
        planFile?.let { put("plan-file", it) }
        failOnViolation?.let { put("fail-on-violation", it) }
        maxCycles?.let { put("max-cycles", it) }
    }
}
