package no.f12.codenavigator.gradle

import no.f12.codenavigator.navigation.refactor.MoveClassFormatter
import no.f12.codenavigator.navigation.refactor.MoveClassResult
import no.f12.codenavigator.navigation.refactor.MoveFileConfig
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
abstract class MoveFileTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : DefaultTask() {

    @get:Classpath
    abstract val openRewriteClasspath: ConfigurableFileCollection

    @TaskAction
    fun moveFile() {
        val config = MoveFileConfig.parse(
            project.buildPropertyMap(TaskRegistry.MOVE_FILE_TASK),
        )

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val classpathDirs = project.taggedClassDirectories().map { (dir, _) -> dir.absolutePath }
        val resultFileLocation = temporaryDir.resolve("move-result.json")

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(openRewriteClasspath)
        }

        workQueue.submit(MoveFileWorkAction::class.java) {
            fromFile.set(config.fromFile)
            toPackage.set(config.toPackage)
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
            text = { MoveClassFormatter.formatFileMove(result, config) },
            json = { MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.JSON)) },
            llm = { MoveClassFormatter.formatFileMove(result, config.copy(format = no.f12.codenavigator.config.OutputFormat.LLM)) },
        ))
    }

    private fun noResultsHints(config: MoveFileConfig): List<String> = buildList {
        add("Ensure the file path is relative to the project root (e.g., src/main/kotlin/com/example/Foo.kt).")
        add("Check that the file exists and contains a package declaration.")
        add("Only Kotlin source files (.kt) are supported.")
    }
}
