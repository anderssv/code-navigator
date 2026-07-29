package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.navigation.refactor.MoveClassFormatter
import no.f12.codenavigator.navigation.refactor.MoveClassResult
import no.f12.codenavigator.navigation.refactor.MoveFileConfig
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@DisableCachingByDefault(because = "Modifies source files")
abstract class MoveFileTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : CodeNavigatorTask() {

    @get:Classpath
    abstract val psiClasspath: ConfigurableFileCollection

    @Option(option = "from-file", description = "Relative path to the source file to move (e.g. src/main/kotlin/com/example/Foo.kt)")
    @get:Internal
    var fromFile: String? = null

    @Option(option = "to-package", description = "Target package (dot-separated)")
    @get:Internal
    var toPackage: String? = null

    @Option(option = "preview", description = "Preview changes without writing to source files")
    @get:Internal
    var preview: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        fromFile?.let { put("from-file", it) }
        toPackage?.let { put("to-package", it) }
        if (preview) put("preview", "true")
    }

    @TaskAction
    fun moveFile() {
        val config = try {
            MoveFileConfig.parse(
                TaskRegistry.MOVE_FILE_TASK.enhanceProperties(buildOptionsMap()),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.MOVE_FILE_TASK.usageHint(BuildTool.GRADLE)}\n" +
                    TaskRegistry.MOVE_FILE_TASK.renderExamples(BuildTool.GRADLE).joinToString("\n"),
            )
        }

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val classpathDirs = project.taggedClassDirectories().map { (dir, _) -> dir.absolutePath }
        val resultFileLocation = temporaryDir.resolve("move-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(psiClasspath)
        }

        workQueue.submit(MoveFileWorkAction::class.java) {
            fromFile.set(config.fromFile)
            toPackage.set(config.toPackage)
            preview.set(config.preview)
            sourceRoots.set(sourceRootPaths)
            this.classpathDirs.set(classpathDirs)
            resultFile.set(resultFileLocation)
        }

        try {
            workQueue.await()
        } catch (e: Exception) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "move-file failed: ${e.message ?: "unknown error"}", noResultsHints(config)))
            return
        }

        if (!resultFileLocation.exists()) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "move-file failed: no result produced.", noResultsHints(config)))
            return
        }

        val result = MoveClassResult.fromJson(resultFileLocation.readText())

        if (result.changes.isEmpty()) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> MoveClassFormatter.formatFileMove(result, config)
                OutputFormat.JSON -> MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
                OutputFormat.LLM -> MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
            }
        })
    }

    private fun noResultsHints(config: MoveFileConfig): List<String> = buildList {
        add("Ensure the file path is relative to the project root (e.g., src/main/kotlin/com/example/Foo.kt).")
        add("Check that the file exists and contains a package declaration.")
        add("Only Kotlin source files (.kt) are supported.")
    }
}
