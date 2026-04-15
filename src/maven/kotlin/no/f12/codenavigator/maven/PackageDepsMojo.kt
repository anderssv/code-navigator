package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.dsm.PackageDependencyBuilder
import no.f12.codenavigator.navigation.dsm.PackageDependencyFormatter
import no.f12.codenavigator.navigation.dsm.PackageDepsConfig
import no.f12.codenavigator.navigation.core.SkippedFileReporter
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

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "package")
    private var packagePattern: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    @Parameter(property = "reverse")
    private var reverse: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val config = PackageDepsConfig.parse(TaskRegistry.PACKAGE_DEPS.enhanceProperties(buildPropertyMap()))

        val taggedDirs = project.taggedClassDirectories()
        val filteredDirs = taggedDirs.filter { config.scope.matchesSourceSet(it.second) }
        val classDirectories = filteredDirs.map { it.first }

        if (classDirectories.isEmpty() || classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val result = CallGraphCache.getOrBuild(File(project.build.directory, "cnav/call-graph.cache"), classDirectories)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val filter: ((MethodRef) -> Boolean)? =
            if (config.projectOnly) graph.projectClassFilter() else null

        val deps = PackageDependencyBuilder.build(graph, filter)

        val packages = if (config.packagePattern != null) {
            val matches = deps.findPackages(config.packagePattern)
            if (matches.isEmpty()) {
                println("No packages found matching '${config.packagePattern}'")
                return
            }
            matches
        } else {
            val all = deps.allPackages()
            if (all.isEmpty()) {
                val packageCount = graph.projectClasses().map { it.packageName() }.distinct().size
                val hints = PackageDependencyFormatter.noResultsHints(packageCount)
                println(OutputWrapper.emptyResult(config.format, "No inter-package dependencies found.", hints))
                return
            }
            all
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { PackageDependencyFormatter.format(deps, packages, config.reverse) },
            json = { JsonFormatter.formatPackageDeps(deps, packages, config.reverse) },
            llm = { LlmFormatter.formatPackageDeps(deps, packages, config.reverse) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        packagePattern?.let { put("package", it) }
        projectOnly?.let { put("project-only", it) }
        reverse?.let { put("reverse", it) }
        scope?.let { put("scope", it) }
    }
}
