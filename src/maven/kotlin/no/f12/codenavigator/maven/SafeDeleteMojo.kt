package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.refactor.SafeDeleteConfig
import no.f12.codenavigator.navigation.refactor.SafeDeleteFormatter
import no.f12.codenavigator.navigation.refactor.SafeDeleteRewriter
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "safe-delete", requiresDependencyResolution = ResolutionScope.COMPILE)
class SafeDeleteMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "target-class")
    private var targetClass: String? = null

    @Parameter(property = "method")
    private var method: String? = null

    @Parameter(property = "preview")
    private var preview: String? = null

    override fun execute() {
        val config = try {
            SafeDeleteConfig.parse(TaskRegistry.SAFE_DELETE_TASK.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
        } catch (e: IllegalArgumentException) {
            println(OutputWrapper.emptyResult(OutputFormat.LLM, "safe-delete failed: ${e.message}",
                TaskRegistry.SAFE_DELETE_TASK.renderExamples(BuildTool.MAVEN)))
            return
        }

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val classDirectories = listOf(File(project.build.outputDirectory))
            .filter { it.exists() }

        val result = SafeDeleteRewriter.delete(
            sourceRoots = sourceRoots,
            classDirectories = classDirectories,
            className = config.className,
            methodName = config.methodName,
            preview = config.preview,
        )

        if (!result.deleted && result.changes.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, result.reason ?: "Cannot delete.", noResultsHints(config)))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> SafeDeleteFormatter.format(result, config)
        OutputFormat.JSON -> SafeDeleteFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
        OutputFormat.LLM -> SafeDeleteFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
    }
})
    }

    private fun noResultsHints(config: SafeDeleteConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        if (config.methodName != null) add("Check that the method '${config.methodName}' exists in '${config.className}'.")
        add("The project must be compiled before running safe-delete (bytecode analysis required).")
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        targetClass?.let { put("target-class", it) }
        method?.let { put("method", it) }
        preview?.let { put("preview", it) }
    }
}
