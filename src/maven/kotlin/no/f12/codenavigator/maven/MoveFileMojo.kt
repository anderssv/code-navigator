package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.refactor.MoveClassFormatter
import no.f12.codenavigator.navigation.refactor.MoveFileConfig
import no.f12.codenavigator.navigation.refactor.MoveFileRewriter
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

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "from-file")
    private var fromFile: String? = null

    @Parameter(property = "to-package")
    private var toPackage: String? = null

    @Parameter(property = "preview")
    private var preview: String? = null

    override fun execute() {
        val config = MoveFileConfig.parse(TaskRegistry.MOVE_FILE_TASK.enhanceProperties(buildPropertyMap()))

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val classpath = listOfNotNull(
            File(project.build.outputDirectory).takeIf { it.exists() }?.toPath(),
            File(project.build.testOutputDirectory).takeIf { it.exists() }?.toPath(),
        )

        val result = MoveFileRewriter.move(
            sourceRoots = sourceRoots,
            fromFile = config.fromFile,
            toPackage = config.toPackage,
            classpath = classpath,
            preview = config.preview,
        )

        if (result.changes.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { MoveClassFormatter.formatFileMove(result, config) },
            json = { MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON)) },
            llm = { MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM)) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
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
