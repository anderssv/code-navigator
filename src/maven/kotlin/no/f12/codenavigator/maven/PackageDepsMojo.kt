package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.dsm.PackageDependencyFormatter
import no.f12.codenavigator.navigation.dsm.PackageDepsConfig
import no.f12.codenavigator.navigation.dsm.PackageDepsOrchestrator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "package-deps")
@Execute(phase = LifecyclePhase.COMPILE)
class PackageDepsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "package")
    private var packagePattern: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    @Parameter(property = "reverse")
    private var reverse: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = PackageDepsConfig.parse(TaskRegistry.PACKAGE_DEPS.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))

        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.none { config.scope.matchesSourceSet(it.second) && it.first.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val cacheDir = File(project.build.directory, "cnav")
        val output = PackageDepsOrchestrator.run(config, taggedDirs, cacheDir, File(cacheDir, "skipped-files.txt"))
        output.skippedFileWarning?.let { log.warn(it) }

        if (output.emptyMessage != null) {
            println(OutputWrapper.emptyResult(config.format, output.emptyMessage, output.emptyHints))
            return
        }

        println(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> PackageDependencyFormatter.format(output.deps, output.packages, config.reverse)
                    OutputFormat.JSON -> PackageDependencyFormatter.formatJson(output.deps, output.packages, config.reverse)
                    OutputFormat.LLM -> PackageDependencyFormatter.formatLlm(output.deps, output.packages, config.reverse)
                }
            },
        )
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        packagePattern?.let { put("package", it) }
        projectOnly?.let { put("project-only", it) }
        reverse?.let { put("reverse", it) }
        scope?.let { put("scope", it) }
    }
}
