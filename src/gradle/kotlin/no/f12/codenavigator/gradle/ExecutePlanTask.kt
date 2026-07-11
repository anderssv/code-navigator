package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.dsm.PlanMutator
import no.f12.codenavigator.navigation.dsm.PlanStep
import no.f12.codenavigator.navigation.refactor.ExecutePlanConfig
import no.f12.codenavigator.navigation.refactor.ExecutePlanFormatter
import no.f12.codenavigator.navigation.refactor.ExecutePlanResult
import no.f12.codenavigator.navigation.refactor.ExecutePlanStepResult
import no.f12.codenavigator.navigation.refactor.MoveClassResult
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
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
abstract class ExecutePlanTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : CodeNavigatorTask() {

    @get:Classpath
    abstract val psiClasspath: ConfigurableFileCollection

    @Option(option = "preview", description = "Preview changes without writing to source files")
    @get:Internal
    var preview: Boolean = false

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        planFile?.let { put("plan-file", it) }
        if (preview) put("preview", "true")
    }

    @TaskAction
    fun executePlan() {
        val config = try {
            ExecutePlanConfig.parse(
                TaskRegistry.EXECUTE_PLAN_TASK.enhanceProperties(buildOptionsMap()),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.EXECUTE_PLAN_TASK.usageHint(BuildTool.GRADLE)}\n" +
                    TaskRegistry.EXECUTE_PLAN_TASK.renderExamples(BuildTool.GRADLE).joinToString("\n"),
            )
        }

        val planPath = java.io.File(config.planFile)
        val steps = PlanMutator.parseFile(planPath)

        if (steps.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "Plan file contains no steps.", emptyList()))
            return
        }

        val classesRoots = project.taggedClassDirectories().map { (dir, _) -> dir }
        val resolvedSteps = resolveClassNames(steps, classesRoots)

        val sourceRootPaths = project.sourceDirectories().map { it.absolutePath }
        val classpathDirs = classesRoots.map { it.absolutePath }

        val stepResults = mutableListOf<ExecutePlanStepResult>()

        for (step in resolvedSteps) {
            when (step) {
                is PlanStep.Move -> {
                    val from = step.classToMove.value
                    val simpleName = step.classToMove.simpleName()
                    val to = "${step.targetPackage.value}.$simpleName"

                    val resultFile = temporaryDir.resolve("plan-step-${stepResults.size}.json")

                    val workQueue = workerExecutor.classLoaderIsolation {
                        classpath.from(psiClasspath)
                    }

                    workQueue.submit(MoveClassWorkAction::class.java) {
                        this.from.set(from)
                        this.to.set(to)
                        this.preview.set(config.preview)
                        this.sourceRoots.set(sourceRootPaths)
                        this.classpathDirs.set(classpathDirs)
                        this.resultFile.set(resultFile)
                    }

                    workQueue.await()

                    val result = MoveClassResult.fromJson(resultFile.readText())
                    stepResults.add(ExecutePlanStepResult(from = from, to = to, result = result))
                }
            }
        }

        val planResult = ExecutePlanResult(steps = stepResults, preview = config.preview)

        if (planResult.totalChanges == 0) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No changes needed for any step.", emptyList()))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
            ExecutePlanFormatter.format(planResult, format)
        })
    }

    private fun resolveClassNames(steps: List<PlanStep>, classesRoots: List<java.io.File>): List<PlanStep> {
        val allClassNames = scanProjectClasses(classesRoots).map { it.value }.toSet()

        return steps.map { step ->
            when (step) {
                is PlanStep.Move -> {
                    val resolved = resolveClassName(step.classToMove, allClassNames)
                    PlanStep.Move(resolved, step.targetPackage)
                }
            }
        }
    }

    private fun resolveClassName(className: ClassName, allClassNames: Set<String>): ClassName {
        // Already fully qualified and exists
        if (className.value in allClassNames) return className

        // Try suffix match (e.g., "api.Dto" matches "com.example.api.Dto")
        val suffix = ".${className.value}"
        val matches = allClassNames.filter { it.endsWith(suffix) || it == className.value }

        return when {
            matches.size == 1 -> ClassName(matches.single())
            matches.isEmpty() -> throw GradleException(
                "Class '${className.value}' not found in project. Provide a fully qualified class name.",
            )
            else -> throw GradleException(
                "Class '${className.value}' is ambiguous, matches: ${matches.joinToString(", ")}. Provide a fully qualified class name.",
            )
        }
    }
}
