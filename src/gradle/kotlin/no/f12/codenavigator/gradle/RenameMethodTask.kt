package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.RenameMethodConfig
import no.f12.codenavigator.navigation.refactor.RenameMethodFormatter
import no.f12.codenavigator.navigation.refactor.RenameMethodResult
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
abstract class RenameMethodTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : DefaultTask() {

    @get:Classpath
    abstract val openRewriteClasspath: ConfigurableFileCollection

    @TaskAction
    fun renameMethod() {
        val config = RenameMethodConfig.parse(
            project.buildPropertyMap(TaskRegistry.RENAME_METHOD_TASK),
        )

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val resultFileLocation = temporaryDir.resolve("rename-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(openRewriteClasspath)
        }

        workQueue.submit(RenameMethodWorkAction::class.java) {
            className.set(config.className)
            methodName.set(config.methodName)
            newName.set(config.newName)
            preview.set(!config.apply)
            sourceRoots.set(sourceRootPaths)
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = RenameMethodResult.fromJson(resultFileLocation.readText())

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { RenameMethodFormatter.format(result, config) },
            json = { RenameMethodFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON)) },
            llm = { RenameMethodFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM)) },
        ))
    }

    private fun noResultsHints(config: RenameMethodConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that the method '${config.methodName}' exists in '${config.className}'.")
        add("Only Kotlin source files (.kt) are searched.")
    }
}
