package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.StrengthConfig
import no.f12.codenavigator.navigation.dsm.StrengthOrchestrator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "strength", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.COMPILE)
class IntegrationStrengthMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "package-filter")
    private var packageFilter: String? = null

    @Parameter(property = "include-external")
    private var includeExternal: String? = null

    @Parameter(property = "dsm-depth")
    private var dsmDepth: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = StrengthConfig.parse(TaskRegistry.STRENGTH.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")

        val classpath = if (config.includeExternal) {
            @Suppress("UNCHECKED_CAST")
            (project.runtimeClasspathElements as? List<String> ?: emptyList())
                .map { File(it).toPath() }
        } else {
            emptyList()
        }

        val output = StrengthOrchestrator.run(config, classDirectories, reportFile, classpath)

        output.skippedFileWarning?.let { log.warn(it) }
        DsmOutputFormatter.format(output, config.format)?.let { println(it) }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        packageFilter?.let { put("package-filter", it) }
        includeExternal?.let { put("include-external", it) }
        dsmDepth?.let { put("dsm-depth", it) }
        top?.let { put("top", it) }
        scope?.let { put("scope", it) }
    }
}
