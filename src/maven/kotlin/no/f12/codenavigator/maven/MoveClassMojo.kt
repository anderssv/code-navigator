package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.refactor.MoveClassConfig
import no.f12.codenavigator.navigation.refactor.MoveClassFormatter
import no.f12.codenavigator.navigation.refactor.MoveClassRewriter
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.Path

@Mojo(name = "move-class")
class MoveClassMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "target-class")
    private var targetClass: String? = null

    @Parameter(property = "new-package")
    private var newPackage: String? = null

    @Parameter(property = "preview")
    private var preview: String? = null

    override fun execute() {
        val config = MoveClassConfig.parse(TaskRegistry.MOVE_CLASS_TASK.enhanceProperties(buildPropertyMap()))

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val classpath = listOfNotNull(
            File(project.build.outputDirectory).takeIf { it.exists() }?.toPath(),
            File(project.build.testOutputDirectory).takeIf { it.exists() }?.toPath(),
        )

        val result = MoveClassRewriter.move(
            sourceRoots = sourceRoots,
            className = config.className,
            newPackage = config.newPackage,
            classpath = classpath,
            preview = config.preview,
        )

        if (result.changes.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { MoveClassFormatter.format(result, config) },
            json = { MoveClassFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON)) },
            llm = { MoveClassFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM)) },
        ))
    }

    private fun noResultsHints(config: MoveClassConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that a class named '${config.className.substringAfterLast(".")}' exists in package '${config.className.substringBeforeLast(".")}'.")
        add("Only Kotlin source files (.kt) are searched.")
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        targetClass?.let { put("target-class", it) }
        newPackage?.let { put("new-package", it) }
        preview?.let { put("preview", it) }
    }
}
