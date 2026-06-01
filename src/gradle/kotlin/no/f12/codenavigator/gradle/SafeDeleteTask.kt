package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.navigation.refactor.SafeDeleteConfig
import no.f12.codenavigator.navigation.refactor.SafeDeleteFormatter
import no.f12.codenavigator.navigation.refactor.SafeDeleteResult
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@DisableCachingByDefault(because = "Modifies source files")
abstract class SafeDeleteTask @Inject constructor(
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

    @Option(option = "preview", description = "Preview changes without writing to source files")
    @get:Internal
    var preview: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        targetClass?.let { put("target-class", it) }
        method?.let { put("method", it) }
        if (preview) put("preview", "true")
    }

    @TaskAction
    fun safeDelete() {
        val config = SafeDeleteConfig.parse(
            TaskRegistry.SAFE_DELETE_TASK.enhanceProperties(buildOptionsMap()),
        )

        val sourceSets = project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.filter { it.exists() }.map { it.absolutePath }
        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val resultFileLocation = temporaryDir.resolve("safe-delete-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(psiClasspath)
        }

        workQueue.submit(SafeDeleteWorkAction::class.java) {
            className.set(config.className)
            methodName.set(config.methodName ?: "")
            this.preview.set(config.preview)
            sourceRoots.set(sourceRootPaths)
            this.classDirectories.set(classDirectories)
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = SafeDeleteResult.fromJson(resultFileLocation.readText())

        if (!result.deleted && result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, result.reason ?: "Cannot delete.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> SafeDeleteFormatter.format(result, config)
        OutputFormat.JSON -> SafeDeleteFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
        OutputFormat.LLM -> SafeDeleteFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
    }
})
    }

    private fun noResultsHints(config: SafeDeleteConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        if (config.methodName != null) add("Check that the method '${config.methodName}' exists in '${config.className}'.")
        add("The project must be compiled before running safe-delete (bytecode analysis required).")
    }
}
