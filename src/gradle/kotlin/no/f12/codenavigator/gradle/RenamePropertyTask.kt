package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.navigation.refactor.RenamePropertyConfig
import no.f12.codenavigator.navigation.refactor.RenamePropertyFormatter
import no.f12.codenavigator.navigation.refactor.RenamePropertyResult
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
abstract class RenamePropertyTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : CodeNavigatorTask() {

    @get:Classpath
    abstract val openRewriteClasspath: ConfigurableFileCollection

    @Option(option = "target-class", description = "Fully qualified class name")
    @get:Internal
    var targetClass: String? = null

    @Option(option = "property", description = "Current property name")
    @get:Internal
    var property: String? = null

    @Option(option = "new-name", description = "New name")
    @get:Internal
    var newName: String? = null

    @Option(option = "preview", description = "Preview changes without writing to source files")
    @get:Internal
    var preview: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        targetClass?.let { put("target-class", it) }
        property?.let { put("property", it) }
        newName?.let { put("new-name", it) }
        if (preview) put("preview", "true")
    }

    @TaskAction
    fun renameProperty() {
        val config = RenamePropertyConfig.parse(
            TaskRegistry.RENAME_PROPERTY_TASK.enhanceProperties(buildOptionsMap()),
        )

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val resultFileLocation = temporaryDir.resolve("rename-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(openRewriteClasspath)
        }

        workQueue.submit(RenamePropertyWorkAction::class.java) {
            className.set(config.className)
            propertyName.set(config.propertyName)
            this.newName.set(config.newName)
            this.preview.set(config.preview)
            sourceRoots.set(sourceRootPaths)
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = RenamePropertyResult.fromJson(resultFileLocation.readText())

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> RenamePropertyFormatter.format(result, config)
        OutputFormat.JSON -> RenamePropertyFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
        OutputFormat.LLM -> RenamePropertyFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
    }
})
    }

    private fun noResultsHints(config: RenamePropertyConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that the property '${config.propertyName}' exists in '${config.className}'.")
        add("Only Kotlin source files (.kt) are searched.")
    }
}
