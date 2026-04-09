package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.refactor.RenamePropertyConfig
import no.f12.codenavigator.navigation.refactor.RenamePropertyFormatter
import no.f12.codenavigator.navigation.refactor.RenamePropertyRewriter
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "rename-property")
class RenamePropertyMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "target-class")
    private var targetClass: String? = null

    @Parameter(property = "property")
    private var property: String? = null

    @Parameter(property = "new-name")
    private var newName: String? = null

    @Parameter(property = "preview")
    private var preview: String? = null

    override fun execute() {
        val config = RenamePropertyConfig.parse(TaskRegistry.RENAME_PROPERTY_TASK.enhanceProperties(buildPropertyMap()))

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val result = RenamePropertyRewriter.rename(
            sourceRoots = sourceRoots,
            className = config.className,
            propertyName = config.propertyName,
            newName = config.newName,
            preview = config.preview,
        )

        if (result.changes.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { RenamePropertyFormatter.format(result, config) },
            json = { RenamePropertyFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON)) },
            llm = { RenamePropertyFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM)) },
        ))
    }

    private fun noResultsHints(config: RenamePropertyConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that the property '${config.propertyName}' exists in '${config.className}'.")
        add("Only Kotlin source files (.kt) are searched.")
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        targetClass?.let { put("target-class", it) }
        property?.let { put("property", it) }
        newName?.let { put("new-name", it) }
        preview?.let { put("preview", it) }
    }
}
