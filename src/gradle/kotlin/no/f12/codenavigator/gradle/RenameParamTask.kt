package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.navigation.refactor.RenameParamConfig
import no.f12.codenavigator.navigation.refactor.RenameParamFormatter
import no.f12.codenavigator.navigation.refactor.RenameResult
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
abstract class RenameParamTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : CodeNavigatorTask() {

    @get:Classpath
    abstract val psiClasspath: ConfigurableFileCollection

    @Option(option = "target-class", description = "Fully qualified class name")
    @get:Internal
    var targetClass: String? = null

    @Option(option = "method", description = "Method name")
    @get:Internal
    var method: String? = null

    @Option(option = "param", description = "Current parameter name")
    @get:Internal
    var param: String? = null

    @Option(option = "new-name", description = "New name")
    @get:Internal
    var newName: String? = null

    @Option(option = "preview", description = "Preview changes without writing to source files")
    @get:Internal
    var preview: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        targetClass?.let { put("target-class", it) }
        method?.let { put("method", it) }
        param?.let { put("param", it) }
        newName?.let { put("new-name", it) }
        if (preview) put("preview", "true")
    }

    @TaskAction
    fun renameParam() {
        val config = try {
            RenameParamConfig.parse(
                TaskRegistry.RENAME_PARAM_TASK.enhanceProperties(buildOptionsMap()),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.RENAME_PARAM_TASK.usageHint(BuildTool.GRADLE)}\n" +
                    TaskRegistry.RENAME_PARAM_TASK.renderExamples(BuildTool.GRADLE).joinToString("\n"),
            )
        }

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val resultFileLocation = temporaryDir.resolve("rename-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(psiClasspath)
        }

        workQueue.submit(RenameParamWorkAction::class.java) {
            className.set(config.className)
            methodName.set(config.methodName)
            paramName.set(config.paramName)
            this.newName.set(config.newName)
            this.preview.set(config.preview)
            sourceRoots.set(sourceRootPaths)
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = RenameResult.fromJson(resultFileLocation.readText())

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> RenameParamFormatter.format(result, config)
        OutputFormat.JSON -> RenameParamFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
        OutputFormat.LLM -> RenameParamFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
    }
})
    }

    private fun noResultsHints(config: RenameParamConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that the method '${config.methodName}' exists in '${config.className}' and has a parameter named '${config.paramName}'.")
        add("Only Kotlin source files (.kt) are searched.")
    }
}
