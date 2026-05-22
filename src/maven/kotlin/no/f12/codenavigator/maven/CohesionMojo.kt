package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.CohesionConfig
import no.f12.codenavigator.navigation.dsm.CohesionOrchestrator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "cohesion")
@Execute(phase = LifecyclePhase.COMPILE)
class CohesionMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = CohesionConfig.parse(TaskRegistry.COHESION.enhanceProperties(buildPropertyMap()))

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        val output = CohesionOrchestrator.run(config, classDirectories, reportFile)

        output.skippedFileWarning?.let { log.warn(it) }
        DsmOutputFormatter.format(output, config.format)?.let { println(it) }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        packageFilter?.let { put("package-filter", it) }
        top?.let { put("top", it) }
        scope?.let { put("scope", it) }
    }
}
