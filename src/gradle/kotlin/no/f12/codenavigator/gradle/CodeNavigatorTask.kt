package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.CnavConfig
import no.f12.codenavigator.navigation.dsm.PlanMutator
import no.f12.codenavigator.navigation.dsm.PlanStep
import no.f12.codenavigator.navigation.dsm.PackageDependency
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.AnalysisWorkspace
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.options.Option
import java.io.File

/** Base for read-only analyses that can resolve a single- or multi-module [AnalysisWorkspace]. */
abstract class WorkspaceAnalysisTask : CodeNavigatorTask() {
    final override fun taskOptionsMap(): Map<String, String?> = buildMap {
        putAll(analysisOptionsMap())
    }

    protected open fun analysisOptionsMap(): Map<String, String?> = emptyMap()
}

/**
 * Base class for all code-navigator tasks.
 * Provides shared options (format, plan-file), a helper to build the options map
 * that Config.parse() methods expect, and detection of legacy -P property usage.
 */
abstract class CodeNavigatorTask : DefaultTask() {

    @Option(option = "format", description = "Output format: text, json, llm, or diff")
    @get:Internal
    var format: String? = null

    @Option(option = "plan-file", description = "JSON file with virtual refactoring steps to apply before analysis")
    @get:Internal
    var planFile: String? = null

    /**
     * Builds the property map from @Option-annotated fields on this task.
     * Subclasses override [taskOptionsMap] to add task-specific options.
     * Also checks for legacy -P property usage and fails with a helpful message.
     *
     * cnav-config.json's `defaults` section is merged in underneath — explicit task options
     * always win. See [CnavConfig].
     */
    protected fun buildOptionsMap(): Map<String, String?> {
        checkForLegacyProperties()
        val map = mutableMapOf<String, String?>()
        format?.let { map["format"] = it }
        planFile?.let { map["plan-file"] = it }
        map.putAll(taskOptionsMap())
        return CnavConfig.applyDefaults(map, project.projectDir)
    }

    /**
     * Loads and applies the plan file to a dependency list. Returns the mutated list.
     * If no plan file is specified, returns dependencies unchanged.
     */
    protected fun applyPlan(dependencies: List<PackageDependency>, dropSamePackageEdges: Boolean = true): List<PackageDependency> {
        val plan = loadPlanSteps()
        if (plan.isEmpty()) return dependencies
        logger.quiet("  Applying plan: ${plan.size} step(s) from $planFile")
        return PlanMutator.apply(dependencies, plan, dropSamePackageEdges)
    }

    protected fun loadPlanSteps(): List<no.f12.codenavigator.navigation.dsm.PlanStep> {
        val path = planFile ?: return emptyList()
        return PlanMutator.parseFile(project.file(path))
    }

    /** Resolve single- or multi-module inputs once, before the analysis orchestrator runs. */
    protected fun resolveAnalysisWorkspace(): AnalysisWorkspace =
        AnalysisWorkspaceResolver.resolve(project)

    /**
     * Override to provide task-specific options as a map.
     * Keys must match TaskRegistry param names.
     */
    protected open fun taskOptionsMap(): Map<String, String?> = emptyMap()

    /**
     * Detects if the user passed -P properties that match known cnav parameter names.
     * If so, fails with a message explaining the new --option syntax.
     */
    private fun checkForLegacyProperties() {
        val allCnavParamNames = TaskRegistry.ALL_TASKS.flatMap { it.params }.map { it.name }.toSet()
        val cliProperties = project.gradle.startParameter.projectProperties.keys
        val legacyParams = cliProperties.filter { it in allCnavParamNames }.sorted()

        if (legacyParams.isNotEmpty()) {
            val examples = legacyParams.joinToString("\n") { param ->
                val value = project.gradle.startParameter.projectProperties[param]
                if (value != null) "  -P$param=$value  →  --$param=$value" else "  -P$param  →  --$param"
            }
            throw GradleException(
                "code-navigator no longer uses -P properties. Use task options instead:\n\n" +
                    "$examples\n\n" +
                    "Example: ./gradlew ${name} --${legacyParams.first()}=<value>\n" +
                    "Run './gradlew help --task ${name}' to see all available options.",
            )
        }
    }
}
