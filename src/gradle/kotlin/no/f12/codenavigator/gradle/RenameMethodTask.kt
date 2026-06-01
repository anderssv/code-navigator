package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.navigation.refactor.RenameLocationFinder
import no.f12.codenavigator.navigation.refactor.RenameMethodConfig
import no.f12.codenavigator.navigation.refactor.RenameMethodFormatter
import no.f12.codenavigator.navigation.refactor.RenameMethodResult
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@DisableCachingByDefault(because = "Modifies source files")
abstract class RenameMethodTask @Inject constructor(
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

    @Option(option = "new-name", description = "New name")
    @get:Internal
    var newName: String? = null

    @Option(option = "preview", description = "Preview changes without writing to source files")
    @get:Internal
    var preview: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        targetClass?.let { put("target-class", it) }
        method?.let { put("method", it) }
        newName?.let { put("new-name", it) }
        if (preview) put("preview", "true")
    }

    @TaskAction
    fun renameMethod() {
        val config = RenameMethodConfig.parse(
            TaskRegistry.RENAME_METHOD_TASK.enhanceProperties(buildOptionsMap()),
        )

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val classesRoots = project.extensions.getByType(SourceSetContainer::class.java)
            .flatMap { it.output.classesDirs.files }
            .filter { it.exists() }

        // Phase 1: Bytecode analysis (runs in main classpath where ASM is available)
        val callSiteFiles = RenameLocationFinder.findCallSiteFiles(classesRoots, config.className, config.methodName)
        val implementorFqns = RenameLocationFinder.findImplementors(classesRoots, config.className)

        val resultFileLocation = temporaryDir.resolve("rename-result.json")

        // Phase 2: PSI rewriting (runs in isolated classpath with kotlin-compiler-embeddable)
        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(psiClasspath)
        }

        workQueue.submit(RenameMethodWorkAction::class.java) {
            className.set(config.className)
            methodName.set(config.methodName)
            this.newName.set(config.newName)
            this.preview.set(config.preview)
            sourceRoots.set(sourceRootPaths)
            this.callSiteFiles.set(callSiteFiles.toList())
            this.implementorFqns.set(implementorFqns.toList())
            resultFile.set(resultFileLocation)
        }

        workQueue.await()

        val result = RenameMethodResult.fromJson(resultFileLocation.readText())

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> RenameMethodFormatter.format(result, config)
        OutputFormat.JSON -> RenameMethodFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON))
        OutputFormat.LLM -> RenameMethodFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM))
    }
})
    }

    private fun noResultsHints(config: RenameMethodConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Use cnavClassDetail --pattern=${config.className.substringAfterLast('.')} to find the FQN if unsure.")
        add("Check that the method '${config.methodName}' exists in '${config.className}' (use cnavClassDetail to verify).")
        add("Only Kotlin source files (.kt) are searched.")
    }
}
