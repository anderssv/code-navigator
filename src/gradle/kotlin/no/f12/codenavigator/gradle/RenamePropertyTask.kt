package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.RenamePropertyConfig
import no.f12.codenavigator.navigation.refactor.RenamePropertyFormatter
import no.f12.codenavigator.navigation.refactor.RenamePropertyResult
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@DisableCachingByDefault(because = "Modifies source files")
abstract class RenamePropertyTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : DefaultTask() {

    @get:Classpath
    abstract val openRewriteClasspath: ConfigurableFileCollection

    @TaskAction
    fun renameProperty() {
        val config = RenamePropertyConfig.parse(
            project.buildPropertyMap(TaskRegistry.RENAME_PROPERTY_TASK),
        )

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val resultFileLocation = temporaryDir.resolve("rename-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(openRewriteClasspath)
        }

        workQueue.submit(RenamePropertyWorkAction::class.java) {
            className.set(config.className)
            propertyName.set(config.propertyName)
            newName.set(config.newName)
            preview.set(config.preview)
            sourceRoots.set(sourceRootPaths)
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = RenamePropertyResult.fromJson(resultFileLocation.readText())

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
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
}
