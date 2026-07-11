package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.refactor.RenameMethodConfig
import no.f12.codenavigator.navigation.refactor.RenameMethodFormatter
import no.f12.codenavigator.navigation.refactor.RenameMethodRewriter
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "rename-method")
class RenameMethodMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "target-class")
    private var targetClass: String? = null

    @Parameter(property = "method")
    private var method: String? = null

    @Parameter(property = "new-name")
    private var newName: String? = null

    @Parameter(property = "preview")
    private var preview: String? = null

    override fun execute() {
        val config = try {
            RenameMethodConfig.parse(TaskRegistry.RENAME_METHOD_TASK.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
        } catch (e: IllegalArgumentException) {
            println(OutputWrapper.emptyResult(OutputFormat.LLM, "rename-method failed: ${e.message}",
                TaskRegistry.RENAME_METHOD_TASK.renderExamples(BuildTool.MAVEN)))
            return
        }

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val classesRoots = listOfNotNull(
            File(project.build.outputDirectory).takeIf { it.exists() },
            File(project.build.testOutputDirectory).takeIf { it.exists() },
        )

        val result = RenameMethodRewriter.rename(
            sourceRoots = sourceRoots,
            className = config.className,
            methodName = config.methodName,
            newName = config.newName,
            preview = config.preview,
            classesRoots = classesRoots,
        )

        if (result.changes.isEmpty()) {
            val diagnosis = RenameMethodRewriter.diagnoseNoChanges(classesRoots, config.className, config.methodName)
            println(OutputWrapper.emptyResult(config.format, diagnosis.message, diagnosis.hints))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> RenameMethodFormatter.format(result, config)
        OutputFormat.JSON -> RenameMethodFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
        OutputFormat.LLM -> RenameMethodFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        targetClass?.let { put("target-class", it) }
        method?.let { put("method", it) }
        newName?.let { put("new-name", it) }
        preview?.let { put("preview", it) }
    }
}
