package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.DocTemplate
import no.f12.codenavigator.registry.ParamDef
import no.f12.codenavigator.registry.TaskCategory
import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.registry.TaskKind
import no.f12.codenavigator.registry.TaskRegistry

/** How a task is grouped in directories and human help; derived, never declared. */
internal enum class HelpGroup(val title: String) {
    BYTECODE("Bytecode analysis (needs compiled classes)"),
    GIT("Git history (no compilation needed)"),
    SOURCE("Source files (no compilation needed)"),
    REFACTOR("Refactoring (rewrites source files)"),
    HELP("Help"),
    ;

    companion object {
        fun of(task: TaskDef): HelpGroup = when {
            task.category == TaskCategory.HELP -> HELP
            task.kind == TaskKind.MUTATION -> REFACTOR
            task.category == TaskCategory.GIT_HISTORY -> GIT
            task.requiresCompilation -> BYTECODE
            else -> SOURCE
        }
    }
}

/**
 * The one place that turns [TaskDef] + [no.f12.codenavigator.registry.TaskDoc] into text.
 * [HelpText] (humans) and [AgentHelpText] (agents) are both thin arrangements of these pieces,
 * so a task documented once is documented everywhere.
 */
internal object HelpRender {

    private val FORMAT_PARAM_NAMES = setOf("format", "llm")

    fun r(template: String, tool: BuildTool): String = DocTemplate.render(template, tool)

    fun tasksIn(group: HelpGroup): List<TaskDef> = TaskRegistry.ALL_TASKS.filter { HelpGroup.of(it) == group }

    fun paramLine(param: ParamDef<*>, tool: BuildTool, task: TaskDef): String {
        val effectiveDefault = task.effectiveDefault(param)
        val annotation = when {
            param.deprecated -> "(deprecated)"
            effectiveDefault != null -> "(optional, default: $effectiveDefault)"
            param.flag || param.optional -> "(optional)"
            else -> "(required)"
        }
        return "${param.render(tool)}  $annotation ${param.description}"
    }

    /** Usage line for the question index and lookups: the task's first example, or the bare task. */
    fun firstUsage(task: TaskDef, tool: BuildTool): String =
        task.examples.firstOrNull()?.render(task.goal, tool) ?: tool.usage(task.goal)

    /**
     * Full documentation block for one task. [full] adds the agent-facing parts (when to use,
     * JSON schema, interpretation, related); human help leaves those out.
     */
    fun taskBlock(task: TaskDef, tool: BuildTool, full: Boolean): String = buildString {
        val doc = task.doc
        val alias = if (task.aliases.isNotEmpty()) " (alias: ${task.aliases.joinToString(", ") { tool.taskName(it) }})" else ""
        appendLine("## ${task.taskName(tool)}$alias")
        appendLine()
        val description = task.description.trimEnd().let { if (it.endsWith('.')) it else "$it." }
        appendLine(description + if (task.requiresCompilation) " Needs compiled classes." else "")
        for (line in doc.details) appendLine(r(line, tool))
        if (full && doc.whenToUse.isNotEmpty()) {
            appendLine()
            appendLine("Use it when asking:")
            for (q in doc.whenToUse) appendLine("- ${r(q, tool)}")
        }
        val params = task.params.filter { it.name !in FORMAT_PARAM_NAMES && !it.deprecated }
        if (params.isNotEmpty()) {
            appendLine()
            appendLine("Parameters:")
            for (p in params) appendLine("  ${paramLine(p, tool, task)}")
        }
        if (task.examples.isNotEmpty()) {
            appendLine()
            appendLine("Examples:")
            for (example in task.examples) appendLine("  ${example.render(task.goal, tool)}")
        }
        if (full && doc.schema.isNotEmpty()) {
            appendLine()
            appendLine("JSON output (${tool.param("format", "json")}):")
            for (line in doc.schema) appendLine("  ${r(line, tool)}")
        }
        if (full && doc.interpretation.isNotEmpty()) {
            appendLine()
            appendLine("Reading the results:")
            for (line in doc.interpretation) appendLine("- ${r(line, tool)}")
        }
        if (doc.caveats.isNotEmpty()) {
            appendLine()
            appendLine("Notes:")
            for (line in doc.caveats) appendLine("- ${r(line, tool)}")
        }
        if (full && doc.related.isNotEmpty()) {
            appendLine()
            appendLine("Related: " + doc.related.joinToString(", ") { tool.taskName(it) })
        }
    }

    /** One line per task, grouped; the whole catalogue in about sixty lines. */
    fun directory(tool: BuildTool, withQuestion: Boolean): String = buildString {
        for (group in HelpGroup.entries) {
            val tasks = tasksIn(group)
            if (tasks.isEmpty()) continue
            appendLine("${group.title}:")
            for (task in tasks) {
                val hint = if (withQuestion) task.doc.whenToUse.firstOrNull()?.let { r(it, tool) } else null
                val text = hint ?: task.description
                appendLine("  ${task.taskName(tool).padEnd(24)} $text")
            }
            appendLine()
        }
    }.trimEnd()

    fun questionIndex(tool: BuildTool): String = buildString {
        for (group in HelpGroup.entries) {
            val tasks = tasksIn(group).filter { it.doc.whenToUse.isNotEmpty() }
            if (tasks.isEmpty()) continue
            appendLine("## ${group.title}")
            appendLine()
            for (task in tasks) {
                for (q in task.doc.whenToUse) appendLine("\"${r(q, tool)}\"")
                appendLine("  → ${firstUsage(task, tool)}")
            }
            appendLine()
        }
    }.trimEnd()

    fun schemas(tool: BuildTool): String = buildString {
        for (task in TaskRegistry.ALL_TASKS.filter { it.doc.schema.isNotEmpty() }) {
            appendLine("${task.taskName(tool)}:")
            for (line in task.doc.schema) appendLine("  ${r(line, tool)}")
            appendLine()
        }
    }.trimEnd()

    fun interpretation(tool: BuildTool): String = buildString {
        for (task in TaskRegistry.ALL_TASKS.filter { it.doc.interpretation.isNotEmpty() }) {
            appendLine("## ${task.taskName(tool)}")
            appendLine()
            for (line in task.doc.interpretation) appendLine("- ${r(line, tool)}")
            appendLine()
        }
    }.trimEnd()

    fun patternMatching(tool: BuildTool): String = buildString {
        val enhanced = TaskRegistry.ALL_TASKS.flatMap { it.params }.filter { it.enhancePattern && !it.deprecated }.map { it.name }.distinct()
        appendLine("All patterns are Java regex, case-insensitive and substring-matched: 'Owner' matches 'org.example.owner.Owner',")
        appendLine("and 'owner\\.Owner' matches the qualified name.")
        appendLine("CamelCase shorthand: at camelCase boundaries '.*' is inserted, so OwnCont matches OwnerController.")
        appendLine("Applied to: ${enhanced.joinToString(", ")}. Other regex params (method, field, filter, exclude, ${tool.taskName("find-string-constant")}'s pattern) are matched as written.")
    }.trimEnd()

    fun feedback(): String = buildString {
        appendLine("To give feedback, ask your agent: \"Review the usage of code-navigator. What was good? What was bad? What could be improved?\"")
        appendLine("Email: cnav@mikill.no    Issues: https://github.com/anderssv/code-navigator/issues")
    }.trimEnd()
}
