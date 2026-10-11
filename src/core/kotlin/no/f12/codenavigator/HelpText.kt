package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry

/**
 * Human-facing help (`cnavHelp`): a compact listing of every command and its options.
 * `task=<goal>` prints one task in full, from the same [HelpRender] as the agent help.
 */
object HelpText {
    fun generate(tool: BuildTool = BuildTool.GRADLE, task: String? = null): String {
        if (task != null) return generateTask(tool, task)
        return generateListing(tool)
    }

    private fun generateTask(tool: BuildTool, goal: String): String {
        val task = TaskRegistry.ALL_TASKS.firstOrNull { it.goal == goal || it.taskName(tool).equals(goal, ignoreCase = true) || it.aliases.contains(goal) }
            ?: return "Unknown task: '$goal'. Valid goals: ${TaskRegistry.ALL_TASKS.joinToString(", ") { it.goal }}"
        return HelpRender.taskBlock(task, tool, full = true).trimEnd()
    }

    private fun generateListing(tool: BuildTool): String = buildString {
        val p = { name: String, value: String -> tool.param(name, value) }
        appendLine("# code-navigator plugin")
        appendLine()
        appendLine("One task in full (parameters, examples, JSON schema, how to read results): ${tool.usage("help", p("task", "<goal>"))}")
        appendLine("All tasks accept ${p("format", "json")} and ${p("format", "llm")}. AI coding agents should run ${tool.usage("agent-help")} instead.")
        appendLine("Optional options are in [brackets]. Patterns are case-insensitive regex with camelCase shorthand (OwnCont matches OwnerController).")
        appendLine()
        for (group in HelpGroup.entries) {
            val tasks = HelpRender.tasksIn(group)
            if (tasks.isEmpty()) continue
            appendLine("## ${group.title}")
            appendLine()
            for (task in tasks) {
                val alias = if (task.aliases.isNotEmpty()) " (alias: ${task.aliases.joinToString(", ") { tool.taskName(it) }})" else ""
                appendLine("${task.taskName(tool).padEnd(24)} ${task.description}$alias")
                val options = task.params
                    .filter { it.name != "format" && it.name != "llm" && !it.deprecated }
                    .joinToString(" ") { param ->
                        val rendered = param.render(tool)
                        if (task.effectiveDefault(param) != null || param.optional || param.flag) "[$rendered]" else rendered
                    }
                if (options.isNotEmpty()) appendLine("    $options")
            }
            appendLine()
        }
        appendLine("## Feedback")
        appendLine()
        appendLine(HelpRender.feedback())
        appendLine()
        appendLine("Made by Anders Sveen / https://www.mikill.no")
    }
}
