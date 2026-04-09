package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.MoveClassConfig
import no.f12.codenavigator.navigation.refactor.MoveClassFormatter
import no.f12.codenavigator.navigation.refactor.MoveClassResult
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
abstract class MoveClassTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : DefaultTask() {

    @get:Classpath
    abstract val openRewriteClasspath: ConfigurableFileCollection

    @TaskAction
    fun moveClass() {
        val config = MoveClassConfig.parse(
            project.buildPropertyMap(TaskRegistry.MOVE_CLASS_TASK),
        )

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val classpathDirs = project.taggedClassDirectories().map { (dir, _) -> dir.absolutePath }
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

        if (result.changes.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed.", noResultsHints(config)))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format,
            text = { MoveClassFormatter.format(result, config) },
            json = { MoveClassFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON)) },
            llm = { MoveClassFormatter.format(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM)) },
        ))
    }

    private fun noResultsHints(config: MoveClassConfig): List<String> = buildList {
        add("Ensure the class name is fully qualified (e.g., com.example.MyClass).")
        add("Check that a class named '${config.fromSimpleName}' exists in package '${config.fromPackage}'.")
        add("Only Kotlin source files (.kt) are searched.")
    }
}
