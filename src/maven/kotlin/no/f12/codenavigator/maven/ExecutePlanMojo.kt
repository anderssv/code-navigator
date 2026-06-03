package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.bytecode.scanProjectClasses
import no.f12.codenavigator.navigation.dsm.PlanMutator
import no.f12.codenavigator.navigation.dsm.PlanStep
import no.f12.codenavigator.navigation.refactor.ExecutePlanConfig
import no.f12.codenavigator.navigation.refactor.ExecutePlanFormatter
import no.f12.codenavigator.navigation.refactor.ExecutePlanResult
import no.f12.codenavigator.navigation.refactor.ExecutePlanStepResult
import no.f12.codenavigator.navigation.refactor.MoveClassRewriter
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "execute-plan")
open class ExecutePlanMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    protected lateinit var project: MavenProject

    @Parameter(property = "format")
    protected var format: String? = null

    @Parameter(property = "plan-file")
    protected var planFile: String? = null

    @Parameter(property = "preview")
    protected var preview: String? = null

    override fun execute() {
        val config = try {
            ExecutePlanConfig.parse(TaskRegistry.EXECUTE_PLAN_TASK.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            println(OutputWrapper.emptyResult(OutputFormat.LLM, "execute-plan failed: ${e.message}",
                TaskRegistry.EXECUTE_PLAN_TASK.renderExamples(BuildTool.MAVEN)))
            return
        }

        val planPath = File(config.planFile)
        val steps = PlanMutator.parseFile(planPath)

        if (steps.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "Plan file contains no steps.", emptyList()))
            return
        }

        val classesRoots = listOfNotNull(
            File(project.build.outputDirectory).takeIf { it.exists() },
            File(project.build.testOutputDirectory).takeIf { it.exists() },
        )
        val resolvedSteps = resolveClassNames(steps, classesRoots)

        val sourceRoots = (project.compileSourceRoots + project.testCompileSourceRoots)
            .map { root -> File(root as String) }
            .filter { it.exists() }

        val classpath = classesRoots.map { it.toPath() }

        val stepResults = mutableListOf<ExecutePlanStepResult>()

        for (step in resolvedSteps) {
            when (step) {
                is PlanStep.Move -> {
                    val from = step.classToMove.value
                    val simpleName = step.classToMove.simpleName()
                    val to = "${step.targetPackage.value}.$simpleName"

                    val result = MoveClassRewriter.move(
                        sourceRoots = sourceRoots,
                        className = from,
                        newFqcn = to,
                        classpath = classpath,
                        preview = config.preview,
                    )

                    stepResults.add(ExecutePlanStepResult(from = from, to = to, result = result))
                }
            }
        }

        val planResult = ExecutePlanResult(steps = stepResults, preview = config.preview)

        if (planResult.totalChanges == 0) {
            println(OutputWrapper.emptyResult(config.format, "No changes needed for any step.", emptyList()))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
            ExecutePlanFormatter.format(planResult, format)
        })
    }

    private fun resolveClassNames(steps: List<PlanStep>, classesRoots: List<File>): List<PlanStep> {
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
        if (className.value in allClassNames) return className

        val suffix = ".${className.value}"
        val matches = allClassNames.filter { it.endsWith(suffix) || it == className.value }

        return when {
            matches.size == 1 -> ClassName(matches.single())
            matches.isEmpty() -> error("Class '${className.value}' not found in project. Provide a fully qualified class name.")
            else -> error("Class '${className.value}' is ambiguous, matches: ${matches.joinToString(", ")}. Provide a fully qualified class name.")
        }
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        planFile?.let { put("plan-file", it) }
        preview?.let { put("preview", it) }
    }
}
