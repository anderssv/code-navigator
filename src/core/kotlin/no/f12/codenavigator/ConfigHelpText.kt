package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskCategory
import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.registry.TaskRegistry

object ConfigHelpText {
    fun generate(tool: BuildTool = BuildTool.GRADLE): String = buildString {
        val propType = when (tool) {
            BuildTool.GRADLE -> "Gradle project properties (-P flags)"
            BuildTool.MAVEN -> "Maven system properties (-D flags)"
        }

        appendLine("=== code-navigator: Configuration Reference ===")
        appendLine()
        appendLine("All parameters are passed as $propType.")
        appendLine("Example: ${tool.usage("find-class", tool.param("pattern", "Service"), tool.param("format", "json"))}")
        if (tool == BuildTool.GRADLE) {
            appendLine()
            appendLine("--- Gradle Configuration Block (persistent defaults) ---")
            appendLine()
            appendLine("  codeNavigator {")
            appendLine("      rootPackage = \"com.example\"   // default: \"\" (all packages)")
            appendLine("  }")
            appendLine()
            appendLine("  Properties set in the config block are used as defaults.")
            appendLine("  -P flags always override the config block.")
        }
        appendLine()
        appendLine("--- Global Parameters (all tasks) ---")
        appendLine()
        appendLine("  ${TaskRegistry.FORMAT.render(tool)}             ${TaskRegistry.FORMAT.description}")
        appendLine("  ${TaskRegistry.LLM.render(tool)}                ${TaskRegistry.LLM.description}")

        val sections = buildParamSections()
        for (section in sections) {
            appendLine()
            appendLine("--- ${section.title} ---")
            appendLine()
            for (entry in section.entries) {
                val rendered = entry.param.render(tool)
                val taskList = entry.tasks
                    .map { it.taskName(tool) }
                    .joinToString(", ")
                val suffix = buildString {
                    append(entry.param.description)
                    if (taskList.isNotEmpty()) {
                        append(" ($taskList")
                        if (entry.defaultNote != null) {
                            append(", ${entry.defaultNote}")
                        }
                        append(")")
                    }
                }
                appendLine("  ${padTo(rendered, 28)}$suffix")
            }
        }
    }

    private fun padTo(s: String, width: Int): String =
        if (s.length >= width) "$s " else s.padEnd(width)

    private data class ParamEntry(
        val param: ParamDef<*>,
        val tasks: List<TaskDef>,
        val defaultNote: String?,
    )

    private data class Section(
        val title: String,
        val entries: List<ParamEntry>,
    )

    private fun buildParamSections(): List<Section> {
        val globalParamNames = TaskRegistry.FORMAT_PARAMS.map { it.name }.toSet()

        val specialtyTasks = setOf(
            TaskRegistry.RANK,
            TaskRegistry.DSM,
            TaskRegistry.CYCLE_DETECTION,
            TaskRegistry.DEAD,
            TaskRegistry.COMPLEXITY,
        )

        val navigationTasks = TaskRegistry.ALL_TASKS
            .filter { it.category == TaskCategory.NAVIGATION && it !in specialtyTasks }

        val navigationParams = collectSharedParams(navigationTasks, globalParamNames)

        val rankParams = collectTaskParams(TaskRegistry.RANK, globalParamNames)

        val dsmParams = collectTaskParams(TaskRegistry.DSM, globalParamNames)

        val cyclesParams = collectTaskParams(TaskRegistry.CYCLE_DETECTION, globalParamNames)

        val deadParams = collectTaskParams(TaskRegistry.DEAD, globalParamNames)

        val complexityParams = collectTaskParams(TaskRegistry.COMPLEXITY, globalParamNames)

        val gitTasks = TaskRegistry.ALL_TASKS
            .filter { it.category == TaskCategory.GIT_HISTORY }
        val gitParams = collectSharedParams(gitTasks, globalParamNames)

        val compositeTasks = TaskRegistry.ALL_TASKS
            .filter { it.category == TaskCategory.COMPOSITE }
        val compositeParams = collectSharedParams(compositeTasks, globalParamNames)

        return listOf(
            Section("Navigation Tasks", navigationParams),
            Section("Type Ranking", rankParams),
            Section("DSM (Dependency Structure Matrix)", dsmParams),
            Section("Cycle Detection (Tarjan's SCC)", cyclesParams),
            Section("Dead Code Detection", deadParams),
            Section("Class Complexity", complexityParams),
            Section("Git History Analysis", gitParams),
            Section("Composite Analysis", compositeParams),
        )
    }

    private fun collectSharedParams(
        tasks: List<TaskDef>,
        excludeNames: Set<String>,
    ): List<ParamEntry> {
        val allParams = tasks.flatMap { task ->
            task.params
                .filter { it.name !in excludeNames }
                .map { param -> param to task }
        }
        val grouped = allParams.groupBy(
            keySelector = { it.first.name },
            valueTransform = { it },
        )
        return grouped.map { (_, pairs) ->
            val param = pairs.first().first
            ParamEntry(
                param = param,
                tasks = pairs.map { it.second },
                defaultNote = param.defaultValue?.let { "default: $it" },
            )
        }
    }

    private fun collectTaskParams(
        task: TaskDef,
        excludeNames: Set<String>,
    ): List<ParamEntry> = task.params
        .filter { it.name !in excludeNames }
        .map { param ->
            ParamEntry(
                param = param,
                tasks = listOf(task),
                defaultNote = param.defaultValue?.let { "default: $it" },
            )
        }
}
