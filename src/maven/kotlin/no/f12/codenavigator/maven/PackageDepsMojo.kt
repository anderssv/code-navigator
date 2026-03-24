package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.CallGraphBuilder
import no.f12.codenavigator.navigation.MethodRef
import no.f12.codenavigator.navigation.PackageDependencyBuilder
import no.f12.codenavigator.navigation.PackageDependencyFormatter
import no.f12.codenavigator.navigation.SkippedFileReporter
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
    private var llm: Boolean? = null

    @Parameter(property = "package")
    private var packagePattern: String? = null

    @Parameter(property = "projectonly", defaultValue = "false")
    private var projectonly: Boolean = false

    @Parameter(property = "reverse", defaultValue = "false")
    private var reverse: Boolean = false

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val outputFormat = OutputFormat.from(format, llm)
        val result = CallGraphBuilder.build(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val filter: ((MethodRef) -> Boolean)? =
            if (projectonly) graph.projectClassFilter() else null

        val deps = PackageDependencyBuilder.build(graph, filter)

        val packages = if (packagePattern != null) {
            val matches = deps.findPackages(packagePattern!!)
            if (matches.isEmpty()) {
                println("No packages found matching '$packagePattern'")
                return
            }
            matches
        } else {
            deps.allPackages()
        }

        val output = when (outputFormat) {
            OutputFormat.JSON -> JsonFormatter.formatPackageDeps(deps, packages, reverse)
            OutputFormat.LLM -> LlmFormatter.formatPackageDeps(deps, packages, reverse)
            OutputFormat.TEXT -> PackageDependencyFormatter.format(deps, packages, reverse)
        }
        println(OutputWrapper.wrap(output, outputFormat))
    }
}
