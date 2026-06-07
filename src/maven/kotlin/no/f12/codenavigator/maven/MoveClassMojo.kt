package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.refactor.MoveClassConfig
import no.f12.codenavigator.navigation.refactor.MoveClassFormatter
import no.f12.codenavigator.navigation.refactor.MoveClassRewriter
import no.f12.codenavigator.navigation.refactor.MoveFileRewriter
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.Path

@Mojo(name = "move-class")
open class MoveClassMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    protected lateinit var project: MavenProject

    @Parameter(property = "format")
    protected var format: String? = null


    @Parameter(property = "from")
    protected var from: String? = null

    @Parameter(property = "to")
    protected var to: String? = null

    @Parameter(property = "from-file")
    protected var fromFile: String? = null

    @Parameter(property = "preview")
    protected var preview: String? = null

    override fun execute() {
        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val classpath = listOfNotNull(
            File(project.build.outputDirectory).takeIf { it.exists() }?.toPath(),
            File(project.build.testOutputDirectory).takeIf { it.exists() }?.toPath(),
        )

        if (fromFile != null) {
            handleFileMove(sourceRoots, classpath)
            return
        }

        val config = try {
            MoveClassConfig.parse(TaskRegistry.MOVE_CLASS_TASK.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            println(OutputWrapper.emptyResult(OutputFormat.LLM, "move-class failed: ${e.message}",
                TaskRegistry.MOVE_CLASS_TASK.renderExamples(BuildTool.MAVEN)))
            return
        }

        val result = MoveClassRewriter.move(
            sourceRoots = sourceRoots,
            className = config.from,
            newFqcn = config.to,
            classpath = classpath,
            preview = config.preview,
        )

        if (result.error != null) {
            println(OutputWrapper.formatAndWrap(config.format) { result.error })
            return
        }

        if (result.changes.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> MoveClassFormatter.format(result, config)
        OutputFormat.JSON -> MoveClassFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
        OutputFormat.LLM -> MoveClassFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
    }
})
    }

    private fun handleFileMove(sourceRoots: List<File>, classpath: List<Path>) {
        if (fromFile == null || to == null) {
            println(OutputWrapper.emptyResult(OutputFormat.LLM, "move-class --from-file requires --to (target package)",
                TaskRegistry.MOVE_CLASS_TASK.renderExamples(BuildTool.MAVEN)))
            return
        }
        val result = MoveFileRewriter.move(
            sourceRoots = sourceRoots,
            fromFile = fromFile!!,
            toPackage = to!!,
            classpath = classpath,
            preview = preview?.toBoolean() ?: false,
        )
        if (result.error != null) {
            println(OutputWrapper.formatAndWrap(format?.let { OutputFormat.from(it) } ?: OutputFormat.TEXT) { result.error })
            return
        }
        if (result.changes.isEmpty()) {
            println(OutputWrapper.emptyResult(format?.let { OutputFormat.from(it) } ?: OutputFormat.TEXT, "No changes needed.", noResultsHints()))
            return
        }
        val outputFormat = format?.let { OutputFormat.from(it) } ?: OutputFormat.TEXT
        println(OutputWrapper.formatAndWrap(outputFormat) { fmt ->
            when (fmt) {
                OutputFormat.TEXT, OutputFormat.DIFF -> MoveClassFormatter.formatFileMove(result,
                    no.f12.codenavigator.navigation.refactor.MoveFileConfig(fromFile = fromFile!!, toPackage = to!!, preview = preview?.toBoolean() ?: false, format = outputFormat))
                OutputFormat.JSON -> MoveClassFormatter.formatFileMove(result,
                    no.f12.codenavigator.navigation.refactor.MoveFileConfig(fromFile = fromFile!!, toPackage = to!!, preview = preview?.toBoolean() ?: false, format = no.f12.codenavigator.config.OutputFormat.JSON))
                OutputFormat.LLM -> MoveClassFormatter.formatFileMove(result,
                    no.f12.codenavigator.navigation.refactor.MoveFileConfig(fromFile = fromFile!!, toPackage = to!!, preview = preview?.toBoolean() ?: false, format = no.f12.codenavigator.config.OutputFormat.LLM))
            }
        })
    }

    private fun noResultsHints(config: Any? = null): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("For file moves, check that the path is relative to the project root.")
        add("Only Kotlin source files (.kt) are searched.")
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        from?.let { put("from", it) }
        to?.let { put("to", it) }
        fromFile?.let { put("from-file", it) }
        preview?.let { put("preview", it) }
    }
}
