package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.dsm.DsmConfig
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.DsmFormatter
import no.f12.codenavigator.navigation.dsm.DsmHtmlRenderer
import no.f12.codenavigator.navigation.dsm.DsmMatrixBuilder
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "dsm")
@Execute(phase = LifecyclePhase.COMPILE)
class DsmMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "root-package")
    private var rootPackage: String? = null

    @Parameter(property = "dsm-depth")
    private var dsmDepth: String? = null

    @Parameter(property = "dsm-html")
    private var dsmHtml: String? = null

    @Parameter(property = "cycles")
    private var cycles: String? = null

    @Parameter(property = "cycle")
    private var cycle: String? = null

    override fun execute() {
        val config = DsmConfig.parse(TaskRegistry.DSM.enhanceProperties(buildPropertyMap()))

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

        println(OutputWrapper.formatAndWrap(config.format,
            text = { if (config.cyclesOnly || config.cycleFilter != null) DsmFormatter.formatCycles(matrix, config.cycleFilter) else DsmFormatter.format(matrix) },
            json = { if (config.cyclesOnly || config.cycleFilter != null) JsonFormatter.formatDsmCycles(matrix, config.cycleFilter) else JsonFormatter.formatDsm(matrix) },
            llm = { if (config.cyclesOnly || config.cycleFilter != null) LlmFormatter.formatDsmCycles(matrix, config.cycleFilter) else LlmFormatter.formatDsm(matrix) },
        ))

        if (config.htmlPath != null) {
            val htmlFile = File(project.basedir, config.htmlPath)
            htmlFile.parentFile?.mkdirs()
            htmlFile.writeText(DsmHtmlRenderer.render(matrix))
            println("DSM HTML written to: ${htmlFile.absolutePath}")
        }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        rootPackage?.let { put("root-package", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        dsmHtml?.let { put("dsm-html", it) }
        cycles?.let { put("cycles", it) }
        cycle?.let { put("cycle", it) }
    }
}
