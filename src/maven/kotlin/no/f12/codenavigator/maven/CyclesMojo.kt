package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.dsm.CycleDetector
import no.f12.codenavigator.navigation.dsm.CyclesConfig
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
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

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "root-package")
    private var rootPackage: String? = null

    @Parameter(property = "dsm-depth")
    private var depth: String? = null

    override fun execute() {
        val config = CyclesConfig.parse(TaskRegistry.CYCLE_DETECTION.enhanceProperties(buildPropertyMap()))

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val result = DsmDependencyExtractor.extract(listOf(classesDir), config.rootPackage)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val dependencies = result.data
        val matrix = DsmMatrixBuilder.build(dependencies, config.rootPackage, config.depth)

        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)
        val details = CycleDetector.enrich(cycles, matrix)

        println(OutputWrapper.formatAndWrap(config.format,
            text = { CyclesFormatter.format(details) },
            json = { JsonFormatter.formatCycles(details) },
            llm = { LlmFormatter.formatCycles(details) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        rootPackage?.let { put("root-package", it) }
        depth?.let { put("dsm-depth", it) }
    }
}
