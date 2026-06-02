package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.navigation.refactor.ChangeSignatureConfig
import no.f12.codenavigator.navigation.refactor.ChangeSignatureFormatter
import no.f12.codenavigator.navigation.refactor.ChangeSignatureResult
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
abstract class ChangeSignatureTask @Inject constructor(
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

    @Option(option = "params", description = "New parameter list (e.g. \"limit: Int, offset: Int, query: String\")")
    @get:Internal
    var params: String? = null

    @Option(option = "defaults", description = "Default values for new params at call sites (e.g. \"query=\\\"\\\"\") comma-separated name=value pairs")
    @get:Internal
    var defaults: String? = null

    @Option(option = "preview", description = "Preview changes without writing to source files")
    @get:Internal
    var preview: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        targetClass?.let { put("target-class", it) }
        method?.let { put("method", it) }
        params?.let { put("params", it) }
        defaults?.let { put("defaults", it) }
        if (preview) put("preview", "true")
    }

    @TaskAction
    fun changeSignature() {
        val config = try {
            ChangeSignatureConfig.parse(
                TaskRegistry.CHANGE_SIGNATURE_TASK.enhanceProperties(buildOptionsMap()),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.CHANGE_SIGNATURE_TASK.usageHint(BuildTool.GRADLE)}\n" +
                    TaskRegistry.CHANGE_SIGNATURE_TASK.renderExamples(BuildTool.GRADLE).joinToString("\n"),
            )
        }

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
            this.params.set(config.params)
            this.defaults.set(config.parsedDefaults())
            this.preview.set(config.preview)
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

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> ChangeSignatureFormatter.format(result, config)
        OutputFormat.JSON -> ChangeSignatureFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
        OutputFormat.LLM -> ChangeSignatureFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
    }
})
    }

    private fun noResultsHints(config: ChangeSignatureConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that the method '${config.methodName}' exists in '${config.className}'.")
        add("The project must be compiled before running change-signature (bytecode analysis required).")
        if (config.defaults.isNullOrBlank()) {
            add("If adding new parameters, provide defaults for existing call sites via --defaults=\"name=value\".")
        }
    }
}
