package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.DsmOutputFormatter
import no.f12.codenavigator.navigation.dsm.BalanceConfig
import no.f12.codenavigator.navigation.dsm.BalanceOrchestrator
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "balance")
@Execute(phase = LifecyclePhase.COMPILE)
class BalanceMojo : AbstractMojo() {

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

    @Parameter(property = "plan-file")
    private var planFile: String? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "min-revs")
    private var minRevs: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    override fun execute() {
        project.checkStaleness(log)

        val config = BalanceConfig.parse(TaskRegistry.BALANCE.enhanceProperties(buildPropertyMap()))

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        val output = BalanceOrchestrator.run(config, classDirectories, reportFile, project.basedir)

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
        planFile?.let { put("plan-file", it) }
        after?.let { put("after", it) }
        minRevs?.let { put("min-revs", it) }
        if (noFollow) put("no-follow", null)
    }
}
