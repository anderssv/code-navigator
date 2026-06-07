package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.navigation.refactor.MoveClassConfig
import no.f12.codenavigator.navigation.refactor.MoveClassFormatter
import no.f12.codenavigator.navigation.refactor.MoveClassResult
import no.f12.codenavigator.navigation.refactor.MoveFileConfig
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.ParamDef
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
abstract class MoveClassTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : CodeNavigatorTask() {

    @get:Classpath
    abstract val openRewriteClasspath: ConfigurableFileCollection

    @Option(option = "from", description = "Fully qualified class name to move/rename")
    @get:Internal
    var from: String? = null

    @Option(option = "to", description = "Target fully qualified class name (or target package when used with --from-file)")
    @get:Internal
    var to: String? = null

    @Option(option = "from-file", description = "Relative path to the source file to move (moves the entire file)")
    @get:Internal
    var fromFile: String? = null

    @Option(option = "preview", description = "Preview changes without writing to source files")
    @get:Internal
    var preview: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        from?.let { put("from", it) }
        to?.let { put("to", it) }
        fromFile?.let { put("from-file", it) }
        if (preview) put("preview", "true")
    }

    @TaskAction
    fun moveClass() {
        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val classpathDirs = project.taggedClassDirectories().map { (dir, _) -> dir.absolutePath }

        if (fromFile != null) {
            handleFileMove(sourceRootPaths, classpathDirs)
            return
        }

        val config = try {
            MoveClassConfig.parse(
                TaskRegistry.MOVE_CLASS_TASK.enhanceProperties(buildOptionsMap()),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.MOVE_CLASS_TASK.usageHint(BuildTool.GRADLE)}\n" +
                    TaskRegistry.MOVE_CLASS_TASK.renderExamples(BuildTool.GRADLE).joinToString("\n"),
            )
        }

        val resultFileLocation = temporaryDir.resolve("move-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(openRewriteClasspath)
        }

        workQueue.submit(MoveClassWorkAction::class.java) {
            from.set(config.from)
            to.set(config.to)
            preview.set(config.preview)
            sourceRoots.set(sourceRootPaths)
            this.classpathDirs.set(classpathDirs)
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = MoveClassResult.fromJson(resultFileLocation.readText())

        if (result.error != null) {
            logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { result.error })
            return
        }

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> MoveClassFormatter.format(result, config)
        OutputFormat.JSON -> MoveClassFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
        OutputFormat.LLM -> MoveClassFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
    }
})
    }

    private fun handleFileMove(sourceRootPaths: List<String>, classpathDirs: List<String>) {
        if (fromFile == null || to == null) {
            throw GradleException("--from-file and --to are required for file moves.\n" +
                TaskRegistry.MOVE_CLASS_TASK.usageHint(BuildTool.GRADLE))
        }
        val config = MoveFileConfig(
            fromFile = fromFile!!,
            toPackage = to!!,
            preview = preview,
            format = ParamDef.parseFormat(buildOptionsMap()),
        )

        val resultFileLocation = temporaryDir.resolve("move-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(openRewriteClasspath)
        }

        workQueue.submit(MoveFileWorkAction::class.java) {
            fromFile.set(config.fromFile)
            toPackage.set(config.toPackage)
            preview.set(config.preview)
            sourceRoots.set(sourceRootPaths)
            this.classpathDirs.set(classpathDirs)
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = MoveClassResult.fromJson(resultFileLocation.readText())

        if (result.error != null) {
            logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { result.error })
            return
        }

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
            when (format) {
                OutputFormat.TEXT, OutputFormat.DIFF -> MoveClassFormatter.formatFileMove(result, config)
                OutputFormat.JSON -> MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
                OutputFormat.LLM -> MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
            }
        })
    }

    private fun noResultsHints(config: Any): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("For file moves, check that the path is relative to the project root.")
        add("Only Kotlin source files (.kt) are searched.")
    }
}
