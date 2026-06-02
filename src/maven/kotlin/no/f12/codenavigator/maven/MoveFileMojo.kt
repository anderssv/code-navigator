package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.refactor.MoveClassFormatter
import no.f12.codenavigator.navigation.refactor.MoveFileConfig
import no.f12.codenavigator.navigation.refactor.MoveFileRewriter
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "move-file")
class MoveFileMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "from-file")
    private var fromFile: String? = null

    @Parameter(property = "to-package")
    private var toPackage: String? = null

    @Parameter(property = "preview")
    private var preview: String? = null

    override fun execute() {
        val config = try {
            MoveFileConfig.parse(TaskRegistry.MOVE_FILE_TASK.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            println(OutputWrapper.emptyResult(OutputFormat.LLM, "move-file failed: ${e.message}",
                TaskRegistry.MOVE_FILE_TASK.renderExamples(BuildTool.MAVEN)))
            return
        }

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val classpath = listOfNotNull(
            File(project.build.outputDirectory).takeIf { it.exists() }?.toPath(),
            File(project.build.testOutputDirectory).takeIf { it.exists() }?.toPath(),
        )

        val result = try {
            MoveFileRewriter.move(
                sourceRoots = sourceRoots,
                fromFile = config.fromFile,
                toPackage = config.toPackage,
                classpath = classpath,
                preview = config.preview,
            )
        } catch (e: Exception) {
            println(OutputWrapper.emptyResult(config.format, "move-file failed: ${e.message ?: "unknown error"}", noResultsHints(config)))
            return
        }

        if (result.changes.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> MoveClassFormatter.formatFileMove(result, config)
                OutputFormat.JSON -> MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
                OutputFormat.LLM -> MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
            }
        })
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        fromFile?.let { put("from-file", it) }
        toPackage?.let { put("to-package", it) }
        preview?.let { put("preview", it) }
    }

    private fun noResultsHints(config: MoveFileConfig): List<String> = buildList {
        add("Ensure the file path is relative to the project root.")
        add("Check that the file '${config.fromFile}' exists and contains a package declaration.")
        add("Only Kotlin source files (.kt) are supported.")
    }
}
