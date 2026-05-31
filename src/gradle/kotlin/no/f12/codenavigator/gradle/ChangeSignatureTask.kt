package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.ChangeSignatureConfig
import no.f12.codenavigator.navigation.refactor.ChangeSignatureFormatter
import no.f12.codenavigator.navigation.refactor.ChangeSignatureResult
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
abstract class ChangeSignatureTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : DefaultTask() {

    @get:Classpath
    abstract val psiClasspath: ConfigurableFileCollection

    @TaskAction
    fun changeSignature() {
        val config = ChangeSignatureConfig.parse(
            project.buildPropertyMap(TaskRegistry.CHANGE_SIGNATURE_TASK),
        )

        val sourceSets = project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.filter { it.exists() }.map { it.absolutePath }
        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val resultFileLocation = temporaryDir.resolve("change-signature-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(psiClasspath)
        }

        workQueue.submit(ChangeSignatureWorkAction::class.java) {
            className.set(config.className)
            methodName.set(config.methodName)
            params.set(config.params)
            defaults.set(config.parsedDefaults())
            preview.set(config.preview)
            sourceRoots.set(sourceRootPaths)
            this.classDirectories.set(classDirectories)
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = ChangeSignatureResult.fromJson(resultFileLocation.readText())

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, result.reason ?: "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { ChangeSignatureFormatter.format(result, config) },
            json = { ChangeSignatureFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON)) },
            llm = { ChangeSignatureFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM)) },
        ))
    }

    private fun noResultsHints(config: ChangeSignatureConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that the method '${config.methodName}' exists in '${config.className}'.")
        add("The project must be compiled before running change-signature (bytecode analysis required).")
        if (config.defaults.isNullOrBlank()) {
            add("If adding new parameters, provide defaults for existing call sites via -Pdefaults=\"name=value\".")
        }
    }
}
