package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.WhyDependsBuilder
import no.f12.codenavigator.navigation.dsm.WhyDependsConfig
import no.f12.codenavigator.navigation.dsm.WhyDependsFormatter
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "why-depends")
@Execute(phase = LifecyclePhase.COMPILE)
class WhyDependsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "from-package")
    private var fromPackage: String? = null

    @Parameter(property = "to-package")
    private var toPackage: String? = null

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = WhyDependsConfig.parse(project.applyConfigDefaults(buildPropertyMap()))

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val extractResult = DsmDependencyExtractor.extract(classDirectories, PackageName(""))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(extractResult.skippedFiles, reportFile)?.let { log.warn(it) }

        val result = WhyDependsBuilder.build(
            dependencies = extractResult.data,
            fromPackage = PackageName(config.fromPackage),
            toPackage = PackageName(config.toPackage),
        )

        if (result.edges.isEmpty()) {
            val hints = WhyDependsFormatter.noResultsHints(result.fromPackage, result.toPackage)
            println(OutputWrapper.emptyResult(config.format, "No dependencies found from '${config.fromPackage}' to '${config.toPackage}'.", hints))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> WhyDependsFormatter.format(result)
        OutputFormat.JSON -> WhyDependsFormatter.format(result)
        OutputFormat.LLM -> WhyDependsFormatter.format(result)
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        fromPackage?.let { put("from-package", it) }
        toPackage?.let { put("to-package", it) }
        format?.let { put("format", it) }
        scope?.let { put("scope", it) }
    }
}
