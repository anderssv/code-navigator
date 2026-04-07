package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.RenameParamConfig
import no.f12.codenavigator.navigation.refactor.RenameParamFormatter
import no.f12.codenavigator.navigation.refactor.RenameResult
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
abstract class RenameParamTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : DefaultTask() {

    @get:Classpath
    abstract val openRewriteClasspath: ConfigurableFileCollection

    @TaskAction
    fun renameParam() {
        val config = RenameParamConfig.parse(
            project.buildPropertyMap(TaskRegistry.RENAME_PARAM_TASK),
        )

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val resultFileLocation = temporaryDir.resolve("rename-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(openRewriteClasspath)
        }

        workQueue.submit(RenameParamWorkAction::class.java) {
            className.set(config.className)
            methodName.set(config.methodName)
            paramName.set(config.paramName)
            newName.set(config.newName)
            preview.set(!config.apply)
            sourceRoots.set(sourceRootPaths)
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = RenameResult.fromJson(resultFileLocation.readText())

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { RenameParamFormatter.format(result, config) },
            json = { RenameParamFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON)) },
            llm = { RenameParamFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM)) },
        ))
    }

    private fun noResultsHints(config: RenameParamConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that the method '${config.methodName}' exists in '${config.className}' and has a parameter named '${config.paramName}'.")
        add("Only Kotlin source files (.kt) are searched.")
    }
}
