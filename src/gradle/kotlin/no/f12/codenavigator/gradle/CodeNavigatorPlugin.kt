package no.f12.codenavigator.gradle

import no.f12.codenavigator.TaskDef
import no.f12.codenavigator.TaskRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodeNavigatorPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        project.extensions.create("codeNavigator", CodeNavigatorExtension::class.java)

        for (taskDef in TaskRegistry.ALL_TASKS) {
            val taskClass = TASK_CLASSES[taskDef.goal]
                ?: error("No Gradle task class registered for goal '${taskDef.goal}'")
            project.tasks.register(taskDef.gradleTaskName, taskClass) {
                description = taskDef.description
                group = "code-navigator"
                if (taskDef.requiresCompilation) {
                    dependsOn("classes")
                }
                if (taskDef.requiresTestCompilation) {
                    dependsOn("testClasses")
                }
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
            doFirst { logger.lifecycle("\uD83E\uDDED code-navigator: $name") }
        }
    }

    companion object {
        private val TASK_CLASSES: Map<String, Class<out DefaultTask>> = mapOf(
            "list-classes" to ListClassesTask::class.java,
            "find-class" to FindClassTask::class.java,
            "find-symbol" to FindSymbolTask::class.java,
            "class-detail" to FindClassDetailTask::class.java,
            "find-callers" to FindCallersTask::class.java,
            "find-callees" to FindCalleesTask::class.java,
            "find-interfaces" to FindInterfaceImplsTask::class.java,
            "type-hierarchy" to TypeHierarchyTask::class.java,
            "package-deps" to PackageDepsTask::class.java,
            "dsm" to DsmTask::class.java,
            "cycles" to CyclesTask::class.java,
            "find-usages" to FindUsagesTask::class.java,
            "rank" to RankTask::class.java,
            "dead" to DeadCodeTask::class.java,
            "find-string-constant" to StringConstantTask::class.java,
            "annotations" to AnnotationsTask::class.java,
            "complexity" to ComplexityTask::class.java,
            "metrics" to MetricsTask::class.java,
            "hotspots" to HotspotTask::class.java,
            "churn" to ChurnTask::class.java,
            "code-age" to CodeAgeTask::class.java,
            "authors" to AuthorAnalysisTask::class.java,
            "coupling" to ChangeCouplingTask::class.java,
            "changed-since" to ChangedSinceTask::class.java,
            "context" to ContextTask::class.java,
            "help" to CodeNavigatorHelpTask::class.java,
            "agent-help" to AgentHelpTask::class.java,
            "config-help" to ConfigHelpTask::class.java,
        )
    }
}
