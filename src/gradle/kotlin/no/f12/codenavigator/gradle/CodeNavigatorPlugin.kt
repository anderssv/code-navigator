package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.ClassFileStaleness
import no.f12.codenavigator.registry.StalenessResult
import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

class CodeNavigatorPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        project.extensions.create("codeNavigator", CodeNavigatorExtension::class.java)

        // The PSI-based refactor rewriters (move, rename, safe-delete, change-signature) run in an
        // isolated worker classloader that needs the Kotlin compiler frontend on its classpath.
        val psiDeps = listOf(
            project.dependencies.create("org.jetbrains.kotlin:kotlin-compiler-embeddable:$KOTLIN_COMPILER_VERSION"),
        )
        val psiConfig = project.configurations.detachedConfiguration(*psiDeps.toTypedArray())

        for (taskDef in TaskRegistry.ALL_TASKS) {
            val taskClass = TASK_CLASSES[taskDef.goal]
                ?: error("No Gradle task class registered for goal '${taskDef.goal}'")
            registerTask(project, taskDef.gradleTaskName, taskClass, taskDef, psiConfig)
            for (aliasGradleName in taskDef.aliasGradleTaskNames) {
                registerTask(project, aliasGradleName, taskClass, taskDef, psiConfig)
            }
        }

        // --- Deprecated legacy aliases ---

        for (taskDef in TaskRegistry.ALL_TASKS) {
            val legacy = taskDef.legacyGradleTaskName ?: continue
            project.tasks.register(legacy) {
                dependsOn(taskDef.gradleTaskName)
                group = "code-navigator (deprecated)"
                description = "DEPRECATED: Use ${taskDef.gradleTaskName} instead"
                doFirst {
                    logger.warn("WARNING: '$legacy' is deprecated. Use '${taskDef.gradleTaskName}' instead.")
                }
            }
        }

        // --- Startup indicator for all cnav tasks ---

        project.tasks.matching { it.group?.startsWith("code-navigator") == true }.configureEach {
            doFirst { logger.quiet("\uD83E\uDDED code-navigator: $name") }
        }
    }

    companion object {
        private const val KOTLIN_COMPILER_VERSION = "2.0.21"

        private fun registerTask(
            project: Project,
            taskName: String,
            taskClass: Class<out DefaultTask>,
            taskDef: TaskDef,
            psiConfig: Configuration,
        ) {
            project.tasks.register(taskName, taskClass) {
                description = taskDef.description
                group = "code-navigator"
                if (taskDef.requiresCompilation || taskDef.requiresTestCompilation) {
                    doFirst {
                        val workspace = AnalysisWorkspaceResolver.resolve(project)
                        val stalenessScope = if (taskDef.requiresTestCompilation) no.f12.codenavigator.navigation.types.Scope.ALL else no.f12.codenavigator.navigation.types.Scope.PROD
                        checkStaleness(workspace.sourceDirectories(stalenessScope), workspace.classDirectories(stalenessScope))
                    }
                }
                if (this is RenameParamTask) {
                    psiClasspath.from(psiConfig)
                }
                if (this is RenameMethodTask) {
                    psiClasspath.from(psiConfig)
                }
                if (this is MoveClassTask) {
                    psiClasspath.from(psiConfig)
                }
                if (this is MovePackageTask) {
                    psiClasspath.from(psiConfig)
                }
                if (this is MoveFileTask) {
                    psiClasspath.from(psiConfig)
                }
                if (this is ExecutePlanTask) {
                    psiClasspath.from(psiConfig)
                }
                if (this is RenamePropertyTask) {
                    psiClasspath.from(psiConfig)
                }
                if (this is SafeDeleteTask) {
                    psiClasspath.from(psiConfig)
                }
                if (this is ChangeSignatureTask) {
                    psiClasspath.from(psiConfig)
                }
            }
        }

        internal val TASK_CLASSES: Map<String, Class<out DefaultTask>> = mapOf(
            "list-classes" to ListClassesTask::class.java,
            "find-class" to FindClassTask::class.java,
            "find-symbol" to FindSymbolTask::class.java,
            "class-detail" to FindClassDetailTask::class.java,
            "find-callers" to FindCallersTask::class.java,
            "find-callees" to FindCalleesTask::class.java,
            "find-interfaces" to FindInterfaceImplsTask::class.java,
            "type-hierarchy" to TypeHierarchyTask::class.java,
            "package-deps" to PackageDepsTask::class.java,
            "why-depends" to WhyDependsTask::class.java,
            "dsm" to DsmTask::class.java,
            "cycles" to CyclesTask::class.java,
            "simulate-move" to SimulateMoveTask::class.java,
            "find-usages" to FindUsagesTask::class.java,
            "rank" to RankTask::class.java,
            "dead" to DeadCodeTask::class.java,
            "find-string-constant" to StringConstantTask::class.java,
            "annotations" to AnnotationsTask::class.java,
            "complexity" to ComplexityTask::class.java,
            "class-metrics" to ClassMetricsTask::class.java,
            "metrics" to MetricsTask::class.java,
            "hotspots" to HotspotTask::class.java,
            "churn" to ChurnTask::class.java,
            "code-age" to CodeAgeTask::class.java,
            "authors" to AuthorAnalysisTask::class.java,
            "coupling" to ChangeCouplingTask::class.java,
            "changed-since" to ChangedSinceTask::class.java,
            "context" to ContextTask::class.java,
            "distance" to PackageDistanceTask::class.java,
            "strength" to IntegrationStrengthTask::class.java,
            "cohesion" to CohesionTask::class.java,
            "move-suggest" to MoveSuggestTask::class.java,
            "suggest-structure" to SuggestStructureTask::class.java,
            "move-file" to MoveFileTask::class.java,
            "volatility" to PackageVolatilityTask::class.java,
            "balance" to BalanceTask::class.java,
            "rings" to RingsTask::class.java,
            "type-affinity" to TypeAffinityTask::class.java,
            "report" to ReportTask::class.java,
            "converge" to ConvergeTask::class.java,
            "size" to SizeTask::class.java,
            "duplicates" to DuplicatesTask::class.java,
            "test-coupling" to TestCouplingTask::class.java,
            "rename-param" to RenameParamTask::class.java,
            "rename-method" to RenameMethodTask::class.java,
            "move-class" to MoveClassTask::class.java,
            "move-package" to MovePackageTask::class.java,
            "rename-property" to RenamePropertyTask::class.java,
            "safe-delete" to SafeDeleteTask::class.java,
            "change-signature" to ChangeSignatureTask::class.java,
            "execute-plan" to ExecutePlanTask::class.java,
            "help" to CodeNavigatorHelpTask::class.java,
            "agent-help" to AgentHelpTask::class.java,
            "config-help" to ConfigHelpTask::class.java,
        )
    }
}

private fun DefaultTask.checkStaleness(sourceDirectories: List<java.io.File>, classDirectories: List<java.io.File>) {
    when (val result = ClassFileStaleness.check(sourceDirectories, classDirectories)) {
        is StalenessResult.Fresh -> {}
        is StalenessResult.Stale -> logger.warn("⚠ ${result.warning}")
        is StalenessResult.NoClassFiles -> throw org.gradle.api.GradleException(result.error)
    }
}
