package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.refactor.RenameMethodConfig
import no.f12.codenavigator.navigation.refactor.RenameMethodFormatter
import no.f12.codenavigator.navigation.refactor.RenameMethodRewriter
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

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "target-class")
    private var targetClass: String? = null

    @Parameter(property = "method")
    private var method: String? = null

    @Parameter(property = "new-name")
    private var newName: String? = null

    @Parameter(property = "preview")
    private var preview: String? = null

    override fun execute() {
        val config = RenameMethodConfig.parse(TaskRegistry.RENAME_METHOD_TASK.enhanceProperties(buildPropertyMap()))

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val result = RenameMethodRewriter.rename(
            sourceRoots = sourceRoots,
            className = config.className,
            methodName = config.methodName,
            newName = config.newName,
            preview = config.preview,
        )

        if (result.changes.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { RenameMethodFormatter.format(result, config) },
            json = { RenameMethodFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON)) },
            llm = { RenameMethodFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM)) },
        ))
    }

    private fun noResultsHints(config: RenameMethodConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that the method '${config.methodName}' exists in '${config.className}'.")
        add("Only Kotlin source files (.kt) are searched.")
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        targetClass?.let { put("target-class", it) }
        method?.let { put("method", it) }
        newName?.let { put("new-name", it) }
        preview?.let { put("preview", it) }
    }
}
