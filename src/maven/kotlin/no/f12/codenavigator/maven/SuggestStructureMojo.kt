package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.navigation.dsm.SuggestStructureConfig
import no.f12.codenavigator.navigation.dsm.SuggestStructureOrchestrator
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugin.AbstractMojo
import java.io.File

@Mojo(name = "suggest-structure")
@Execute(phase = LifecyclePhase.COMPILE)
class SuggestStructureMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: org.apache.maven.project.MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "max-fan-in")
    private var maxFanIn: String? = null

    @Parameter(property = "min-group-size")
    private var minGroupSize: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = SuggestStructureConfig.parse(TaskRegistry.SUGGEST_STRUCTURE.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        val output = SuggestStructureOrchestrator.run(config, classDirectories, reportFile)

        output.skippedFileWarning?.let { log.warn(it) }
        DsmOutputFormatter.format(output, config.format)?.let { println(it) }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        packageFilter?.let { put("package-filter", it) }
        top?.let { put("top", it) }
        maxFanIn?.let { put("max-fan-in", it) }
        minGroupSize?.let { put("min-group-size", it) }
        scope?.let { put("scope", it) }
    }
}
