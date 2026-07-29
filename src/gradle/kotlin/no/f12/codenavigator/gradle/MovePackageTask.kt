package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.refactor.ExecutePlanFormatter
import no.f12.codenavigator.navigation.refactor.ExecutePlanResult
import no.f12.codenavigator.navigation.refactor.ExecutePlanStepResult
import no.f12.codenavigator.navigation.refactor.MoveClassResult
import no.f12.codenavigator.navigation.refactor.MovePackageConfig
import no.f12.codenavigator.navigation.types.ClassName
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
abstract class MovePackageTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : CodeNavigatorTask() {

    @get:Classpath
    abstract val psiClasspath: ConfigurableFileCollection

    @Option(option = "from-package", description = "Source package (dot-separated)")
    @get:Internal
    var fromPackage: String? = null

    @Option(option = "to-package", description = "Target package (dot-separated)")
    @get:Internal
    var toPackage: String? = null

    @Option(option = "preview", description = "Preview changes without writing to source files")
    @get:Internal
    var preview: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        fromPackage?.let { put("from-package", it) }
        toPackage?.let { put("to-package", it) }
        if (preview) put("preview", "true")
    }

    @TaskAction
    fun movePackage() {
        val config = try {
            MovePackageConfig.parse(
                TaskRegistry.MOVE_PACKAGE_TASK.enhanceProperties(buildOptionsMap()),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.MOVE_PACKAGE_TASK.usageHint(BuildTool.GRADLE)}\n" +
                    TaskRegistry.MOVE_PACKAGE_TASK.renderExamples(BuildTool.GRADLE).joinToString("\n"),
            )
        }

        val classesRoots = project.taggedClassDirectories().map { (dir, _) -> dir }
        val classesInPackage = scanProjectClasses(classesRoots)
            .filter { ClassName(it.value).packageName().value == config.fromPackage }
            .map { it.value }

        if (classesInPackage.isEmpty()) {
            logger.quiet(
                OutputWrapper.emptyResult(config.format, "No classes found in package '${config.fromPackage}'.", noResultsHints(config)),
            )
            return
        }

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val classpathDirs = classesRoots.map { it.absolutePath }

        val workQueue = workerExecutor.classLoaderIsolation {
            classpath.from(psiClasspath)
        }

        val moves = classesInPackage.map { fqcn ->
            val simpleName = ClassName(fqcn).simpleName()
            fqcn to "${config.toPackage}.$simpleName"
        }
        val resultFile = temporaryDir.resolve("move-package-batch.json")

        workQueue.submit(MoveBatchWorkAction::class.java) {
            this.froms.set(moves.map { it.first })
            this.tos.set(moves.map { it.second })
            this.preview.set(config.preview)
            this.sourceRoots.set(sourceRootPaths)
            this.classpathDirs.set(classpathDirs)
            this.resultFile.set(resultFile)
        }
        workQueue.await()

        val results = MoveClassResult.listFromJson(resultFile.readText())
        val stepResults = moves.zip(results).map { (move, result) ->
            ExecutePlanStepResult(from = move.first, to = move.second, result = result)
        }

        val planResult = ExecutePlanResult(steps = stepResults, preview = config.preview)

        if (planResult.totalChanges == 0) {
            logger.quiet(OutputWrapper.emptyResult(config.format, "No changes needed for any class in package.", noResultsHints(config)))
            return
        }

        logger.quiet(OutputWrapper.formatAndWrap(config.format) { format ->
            ExecutePlanFormatter.format(planResult, format)
        })
    }

    private fun noResultsHints(config: MovePackageConfig): List<String> = buildList {
        add("Ensure the package exists and contains at least one class.")
        add("Package names are dot-separated (e.g., com.example.services).")
        if (!config.preview) add("Try with --preview first to inspect changes before applying.")
    }
}
