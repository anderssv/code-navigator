package no.f12.codenavigator.gradle

import no.f12.codenavigator.registry.TaskRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.options.Option

/**
 * Base class for all code-navigator tasks.
 * Provides shared options (format), a helper to build the options map
 * that Config.parse() methods expect, and detection of legacy -P property usage.
 */
abstract class CodeNavigatorTask : DefaultTask() {

    @Option(option = "format", description = "Output format: text, json, llm, or diff")
    @get:Internal
    var format: String? = null

    /**
     * Builds the property map from @Option-annotated fields on this task.
     * Subclasses override [taskOptionsMap] to add task-specific options.
     * Also checks for legacy -P property usage and fails with a helpful message.
     */
    protected fun buildOptionsMap(): Map<String, String?> {
        checkForLegacyProperties()
        val map = mutableMapOf<String, String?>()
        format?.let { map["format"] = it }
        map.putAll(taskOptionsMap())
        return map
    }

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
