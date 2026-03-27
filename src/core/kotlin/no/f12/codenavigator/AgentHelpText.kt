package no.f12.codenavigator

object AgentHelpText {
    private val VALID_SECTIONS = setOf("install", "workflow", "interpretation", "schemas", "extraction")

    fun generate(tool: BuildTool = BuildTool.GRADLE, section: String? = null): String = when (section) {
        null -> generateCompact(tool)
        "install" -> generateInstall(tool)
        "workflow" -> generateWorkflow(tool)
        "interpretation" -> generateInterpretation(tool)
        "schemas" -> generateSchemas(tool)
        "extraction" -> generateExtraction(tool)
        else -> "Unknown section: '$section'. Valid sections: ${VALID_SECTIONS.sorted().joinToString(", ")}"
    }

    private fun generateInstall(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }

        val helpGoals = setOf("help", "agent-help", "config-help")
        val taskNames = TaskRegistry.ALL_TASKS
            .filter { it.goal !in helpGoals }
            .joinToString(", ") { it.taskName(tool) }

        appendLine("## code-navigator")
        appendLine()
        appendLine("code-navigator analyzes JVM bytecode and git history.")
        appendLine("It gives structural and behavioral insight into a codebase without reading source files.")
        appendLine("Bytecode queries (callers, dependencies, implementors) return accurate, complete results")
        appendLine("in a single call — no iterative searching needed.")
        appendLine()
        appendLine("Available tasks: $taskNames")
        appendLine()
        appendLine("All tasks support ${p("llm", "true")} for compact output and ${p("format", "json")} for structured output.")
        appendLine()
        appendLine("Run ${tool.usage("agent-help")} before first use for task selection guidance.")
        appendLine("Run ${tool.usage("help")} for full parameter documentation.")
    }

    private fun generateCompact(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)

        val pluginType = when (tool) {
            BuildTool.GRADLE -> "Gradle"
            BuildTool.MAVEN -> "Maven"
        }

        appendLine("=== code-navigator: AI Agent Guide ===")
        appendLine()
        appendLine("code-navigator is a $pluginType plugin that analyzes JVM bytecode and git history.")
        appendLine("It gives you structural and behavioral insight into a codebase without reading source files.")
        appendLine("Bytecode queries return accurate, complete results in a single tool call —")
        appendLine("No iterative searching needed. Be correct on the first response.")
        appendLine("All tasks support ${p("llm", "true")} for compact, token-efficient output.")
        appendLine("All tasks also support ${p("format", "json")} for structured, parseable output.")
        appendLine()
        appendLine("--- When to Use What ---")
        appendLine()
        appendLine("Use code-navigator when:")
        appendLine("- Tracing call chains — ${t("find-callers")}/${t("find-callees")} resolve actual invocations")
        appendLine("  through interfaces and inheritance.")
        appendLine("- Understanding dependencies — ${t("package-deps")}/${t("dsm")} give accurate package-level")
        appendLine("  coupling from bytecode analysis.")
        appendLine("- Finding interface implementations — ${t("find-interfaces")} resolves polymorphism.")
        appendLine("- Migration planning — ${t("find-usages")} finds all call sites, field accesses, and type")
        appendLine("  references to external APIs.")
        appendLine("- Inspecting class structure — ${t("class-detail")} shows fields, methods, supertypes")
        appendLine("  without reading source files.")
        appendLine()
        appendLine("Use grep/glob when:")
        appendLine("- Searching for string patterns, variable names, or text in comments")
        appendLine("- Finding files by name")
        appendLine("- Reading implementation logic (method bodies)")
        appendLine()
        appendLine("--- Common Questions → Which Task ---")
        appendLine()
        appendLine("  \"Where is type X used?\"")
        appendLine("    → ${u("find-usages", p("type", "X"))}")
        appendLine()
        appendLine("  \"Where is field X read or written?\"")
        appendLine("    → ${u("find-usages", p("ownerClass", "com.example.Config"), p("field", "timeout"))}")
        appendLine()
        appendLine("  \"Who calls method X?\"")
        appendLine("    → ${u("find-callers", p("method", "X"))}")
        appendLine()
        appendLine("  \"What does class X look like?\"")
        appendLine("    → ${u("class-detail", p("pattern", "X"))}")
        appendLine()
        appendLine("  \"What calls does method X make?\"")
        appendLine("    → ${u("find-callees", p("method", "X"))}")
        appendLine()
        appendLine("  \"Who implements interface X?\"")
        appendLine("    → ${u("find-interfaces", p("pattern", "X"))}")
        appendLine()
        appendLine("  \"What depends on package X?\"")
        appendLine("    → ${u("package-deps", p("package", "X"), p("reverse", "true"))}")
        appendLine()
        appendLine("  \"Is there dead code?\"  → ${u("dead")}")
        appendLine()
        appendLine("  \"What are the most important types?\"  → ${u("rank")}")
        appendLine()
        appendLine("  \"Which files change the most?\"  → ${u("hotspots")}")
        appendLine()
        appendLine("--- Task Reference ---")
        appendLine()
        appendTaskReference(tool)
        appendLine()
        appendLine("--- Global Parameters ---")
        appendLine()
        appendGlobalParameters(tool)
        appendLine()
        appendLine("--- Tips for Optimal Results ---")
        appendLine()
        appendLine("- Always use ${p("llm", "true")} for compact output. Omit only for human-readable display.")
        appendLine("- Use ${p("projectonly", "true")} to cut noise from JDK/stdlib classes.")
        appendLine("- Patterns are Java regex, case-insensitive. Use .* for wildcards.")
        appendLine("- Chain tasks: find a class → inspect it → trace its callers.")
        appendLine("- Results are cached across calls — subsequent runs in the same build are fast.")
        appendLine("- Run ${u("help")} for full parameter documentation.")
        appendLine()
        appendSectionDirectory(tool)
    }

    private fun generateWorkflow(tool: BuildTool): String = buildString {
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)

        appendLine("=== code-navigator: Recommended Workflow ===")
        appendLine()
        appendLine("1. ORIENT: Get a high-level view of the codebase")
        appendLine("   ${u("package-deps")}                        # package dependency map")
        appendLine("   ${u("dsm")}                         # dependency structure matrix")
        appendLine("   ${u("package-deps", p("format", "json"))}           # same, as JSON")
        appendLine("   ${u("list-classes")}                  # all classes and source files")
        appendLine()
        appendLine("2. FIND: Locate relevant code")
        appendLine("   ${u("find-class", p("pattern", "Service"))}  # find classes by name")
        appendLine("   ${u("find-symbol", p("pattern", "reset"))}   # find methods/fields by name")
        appendLine()
        appendLine("3. INSPECT: Understand a class")
        appendLine("   ${u("class-detail", p("pattern", "ResetPasswordService"))}")
        appendLine("   # Shows fields, methods, superclass, and implemented interfaces")
        appendLine()
        appendLine("4. TRACE: Follow the call graph")
        appendLine("   ${u("find-callers", p("method", "resetPassword"), p("maxdepth", "3"), p("projectonly", "true"))}  # who calls this?")
        appendLine("   ${u("find-callees", p("method", "handleRequest"), p("maxdepth", "3"), p("projectonly", "false"))}  # what does this call?")
        appendLine("   # ${p("projectonly", "true")} hides JDK/stdlib noise")
        appendLine("   # ${p("maxdepth", "5")} for deeper traversal")
        appendLine()
        appendLine("5. MAP: Understand abstractions")
        appendLine("   ${u("find-interfaces", p("pattern", "Repository"))}")
        appendLine("   # Add ${p("includetest", "true")} to see test fakes and stubs")
        appendLine()
        appendLine("6. MIGRATE: Find external API usage for migration")
        appendLine("   ${u("find-usages", p("ownerClass", "kotlinx.datetime.LocalDate"), p("method", "getMonthNumber"))}")
        appendLine("   ${u("find-usages", p("ownerClass", "com.example.Config"), p("field", "timeout"))}  # field reads/writes + property accessors")
        appendLine("   ${u("find-usages", p("type", "kotlinx.datetime.Instant"))}")
        appendLine("   # Find all call sites, field accesses, and type references to external types")
        appendLine()
        appendLine("7. ANALYZE: Git history behavioral analysis (no compilation needed)")
        appendLine("   ${u("hotspots")}                     # most-changed files")
        appendLine("   ${u("coupling")}                     # files that change together")
        appendLine("   ${u("code-age")}                          # time since last change per file")
        appendLine("   ${u("authors")}                      # distinct contributors per file")
        appendLine("   ${u("churn")}                        # lines added/deleted per file")
        appendLine("   # All git tasks accept ${p("after", "YYYY-MM-DD")} (default: 1 year ago)")
        appendLine()
        appendLine("8. RANK: Identify structurally important types")
        appendLine("   ${u("rank")}                         # top 50 most important types (PageRank)")
        appendLine("   ${u("rank", p("top", "20"))}                  # top 20")
        appendLine("   ${u("rank", p("projectonly", "false"))}        # include external types")
        appendLine()
        appendLine("9. DEAD CODE: Find unreferenced classes and methods")
        appendLine("   Kotlin noise (data class boilerplate, companion objects, coroutine inner classes) is auto-filtered.")
        appendLine("   ${u("dead")}                         # all potential dead code")
        appendLine("   ${u("dead", p("filter", "service"))}             # only show matches")
        appendLine("   ${u("dead", p("exclude", "\"Main|Test\""))}    # exclude entry points")
        appendLine("   ${u("dead", p("classes-only", "true"))}         # only unreferenced classes")
        appendLine()
        appendLine("10. COMPLEXITY: Measure class coupling (fan-in/fan-out)")
        appendLine("   ${u("complexity")}                        # all classes sorted by fan-out")
        appendLine("   ${u("complexity", p("classname", "Service"))}       # fan-in/out for matching classes")
        appendLine("   ${u("complexity", p("classname", "\".*Controller\""), p("detail", "true"))} # with call details")
        appendLine()
        appendLine("11. METRICS: Quick project health snapshot")
        appendLine("   ${u("metrics")}                       # summary: classes, packages, fan-in/out, cycles, dead code, hotspots")
        appendLine("   ${u("metrics", p("top", "10"))}                # top 10 hotspots")
        appendLine("   ${u("metrics", p("root-package", "com.example"))} # cycle detection scoped to package")
    }

    private fun generateInterpretation(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }

        appendLine("=== code-navigator: Result Interpretation ===")
        appendLine()
        appendLine("Fan-in (${t("complexity")} / ${t("rank")}):")
        appendLine("- High fan-in on a concrete class (>20 callers) → possible god object or central")
        appendLine("  service. Investigate whether it has too many responsibilities.")
        appendLine("- High fan-in on an interface or abstract class → normal. Core domain abstractions")
        appendLine("  are meant to be widely depended on.")
        appendLine("- High fan-in on a repository → normal. Repositories are shared infrastructure.")
        appendLine()
        appendLine("Fan-out (${t("complexity")}):")
        appendLine("- High fan-out (>15 distinct dependencies) → class depends on too many things.")
        appendLine("  Candidate for decomposition — split into focused collaborators.")
        appendLine("- High fan-out in a formatter or controller → often acceptable. These are")
        appendLine("  integration points that coordinate many domain objects.")
        appendLine()
        appendLine("Dead code (${t("dead")}):")
        appendLine("- Classes/methods with framework annotations (@Route, @Scheduled, @Serializable)")
        appendLine("  are likely false positives — invoked via reflection, not direct calls.")
        appendLine("- Methods only called from test code may be test utilities, not dead production code.")
        appendLine("- Companion object members and data class copy()/componentN() are auto-filtered.")
        appendLine()
        appendLine("Change coupling (${t("coupling")}):")
        appendLine("- High coupling degree (>60%) with many shared revisions → strong implicit dependency.")
        appendLine("  Consider extracting shared logic or making the dependency explicit.")
        appendLine("- High coupling degree but few shared revisions (<5) → may be coincidence.")
        appendLine("  Wait for more data before acting.")
        appendLine("- Test file coupled with its production file → expected, not a problem.")
        appendLine()
        appendLine("Hotspots (${t("hotspots")}):")
        appendLine("- Files with high revision count AND structural problems (high fan-out, cycles)")
        appendLine("  are priority refactoring targets — they change often and are hard to change safely.")
        appendLine("- Files with high revision count but clean structure → active development, not a problem.")
    }

    private fun generateSchemas(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }

        appendLine("=== code-navigator: JSON Schemas ===")
        appendLine()
        appendLine("${t("list-classes")} / ${t("find-class")}:")
        appendLine("  [{\"className\": \"com.example.Foo\", \"sourceFile\": \"Foo.kt\", \"sourcePath\": \"com/example/Foo.kt\"}]")
        appendLine()
        appendLine("${t("find-symbol")}:")
        appendLine("  [{\"package\": \"com.example\", \"class\": \"Service\", \"symbol\": \"doWork\", \"kind\": \"method\", \"sourceFile\": \"Service.kt\"}]")
        appendLine()
        appendLine("${t("class-detail")}:")
        appendLine("  [{\"className\": \"...\", \"sourceFile\": \"...\", \"superClass\": \"...\",")
        appendLine("    \"interfaces\": [\"...\"], \"fields\": [{\"name\": \"...\", \"type\": \"...\"}],")
        appendLine("    \"methods\": [{\"name\": \"...\", \"parameters\": [\"...\"], \"returnType\": \"...\"}]}]")
        appendLine()
        appendLine("${t("find-callers")} / ${t("find-callees")}:")
        appendLine("  [{\"method\": \"com.example.Service.doWork\", \"sourceFile\": \"Service.kt\",")
        appendLine("    \"children\": [{\"method\": \"...\", \"sourceFile\": \"...\", \"children\": [...]}]}]")
        appendLine()
        appendLine("${t("find-interfaces")}:")
        appendLine("  [{\"interface\": \"com.example.Repository\",")
        appendLine("    \"implementors\": [{\"className\": \"com.example.SqlRepo\", \"sourceFile\": \"SqlRepo.kt\"}]}]")
        appendLine()
        appendLine("${t("package-deps")}:")
        appendLine("  [{\"package\": \"com.example.services\", \"dependencies\": [\"com.example.domain\"]}]")
        appendLine("  With ${p("reverse", "true")}: \"dependents\" replaces \"dependencies\"")
        appendLine()
        appendLine("${t("find-usages")}:")
        appendLine("  [{\"callerClass\": \"com.example.MyService\", \"callerMethod\": \"process\",")
        appendLine("    \"targetOwner\": \"kotlinx.datetime.LocalDate\", \"targetMethod\": \"getMonthNumber\",")
        appendLine("    \"kind\": \"method_call\", \"sourceFile\": \"MyService.kt\"}]")
        appendLine()
        appendLine("${t("rank")}:")
        appendLine("  [{\"className\": \"com.example.Core\", \"rank\": 0.42, \"inDegree\": 5, \"outDegree\": 2}]")
        appendLine()
        appendLine("${t("dead")}:")
        appendLine("  [{\"className\": \"com.example.Orphan\", \"kind\": \"class\", \"sourceFile\": \"Orphan.kt\"},")
        appendLine("   {\"className\": \"com.example.Service\", \"memberName\": \"unused\", \"kind\": \"method\", \"sourceFile\": \"Service.kt\"}]")
        appendLine()
        appendLine("${t("complexity")}:")
        appendLine("  [{\"className\": \"com.example.Service\", \"sourceFile\": \"Service.kt\",")
        appendLine("    \"fanOut\": 12, \"fanIn\": 5, \"distinctOutgoingClasses\": 4, \"distinctIncomingClasses\": 3,")
        appendLine("    \"outgoingByClass\": [{\"className\": \"com.example.Repo\", \"callCount\": 5}],")
        appendLine("    \"incomingByClass\": [{\"className\": \"com.example.Controller\", \"callCount\": 3}]}]")
        appendLine()
        appendLine("${t("cycles")}:")
        appendLine("  [{\"packages\": [\"api\", \"service\"],")
        appendLine("    \"edges\": [{\"from\": \"api\", \"to\": \"service\",")
        appendLine("      \"classEdges\": [{\"source\": \"api.Controller\", \"target\": \"service.Service\"}]}]}]")
        appendLine()
        appendLine("${t("metrics")}:")
        appendLine("  {\"totalClasses\": 42, \"packageCount\": 8, \"averageFanIn\": 3.2, \"averageFanOut\": 5.1,")
        appendLine("   \"cycleCount\": 1, \"deadClassCount\": 3, \"deadMethodCount\": 7,")
        appendLine("   \"topHotspots\": [{\"file\": \"Service.kt\", \"revisions\": 25, \"totalChurn\": 340}]}")
    }

    private fun generateExtraction(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)

        appendLine("=== code-navigator: Extracting Output ===")
        appendLine()
        val buildToolNote = when (tool) {
            BuildTool.GRADLE -> "When using ${p("llm", "true")} or ${p("format", "json")}, Gradle mixes its own output (task headers,\nwarnings, build status) into stdout. To handle this, output is wrapped with markers:"
            BuildTool.MAVEN -> "When using ${p("llm", "true")} or ${p("format", "json")}, Maven may mix its own output\ninto stdout. To handle this, output is wrapped with markers:"
        }
        appendLine(buildToolNote)
        appendLine()
        appendLine("  ---CNAV_BEGIN---")
        appendLine("  [{\"className\":\"...\"}]")
        appendLine("  ---CNAV_END---")
        appendLine()
        appendLine("Extract the JSON between markers:")
        appendLine("  ${u("list-classes", p("format", "json"))} | sed -n '/CNAV_BEGIN/,/CNAV_END/p'")
        appendLine()
        appendLine("Or define a shell helper to reduce noise:")
        val helperName = "cnav"
        val helperExample = when (tool) {
            BuildTool.GRADLE -> "  $helperName() { ./gradlew \"\$@\" | sed -n '/CNAV_BEGIN/,/CNAV_END/p'; }"
            BuildTool.MAVEN -> "  $helperName() { mvn \"\$@\" | sed -n '/CNAV_BEGIN/,/CNAV_END/p'; }"
        }
        appendLine(helperExample)
        appendLine()
        appendLine("--- jq Examples (using $helperName helper) ---")
        appendLine()
        appendLine("Extract class names:")
        appendLine("  $helperName ${t("list-classes")} ${p("format", "json")} | jq '.[].className'")
        appendLine()
        appendLine("Find source file for a class:")
        appendLine("  $helperName ${t("find-class")} ${p("pattern", "Service")} ${p("format", "json")} | jq '.[].sourcePath'")
        appendLine()
        appendLine("Get caller method names:")
        appendLine("  $helperName ${t("find-callers")} ${p("method", "save")} ${p("maxdepth", "3")} ${p("format", "json")} | jq '.[].children[].method'")
        appendLine()
        appendLine("List packages that depend on domain:")
        appendLine("  $helperName ${t("package-deps")} ${p("package", "domain")} ${p("reverse", "true")} ${p("format", "json")} | jq '.[].dependents[]'")
        appendLine()
        appendLine("Get interface implementor class names:")
        appendLine("  $helperName ${t("find-interfaces")} ${p("pattern", "Repository")} ${p("format", "json")} | jq '.[].implementors[].className'")
    }

    private fun StringBuilder.appendSectionDirectory(tool: BuildTool) {
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)

        appendLine("--- More Detail ---")
        appendLine()
        appendLine("Run with ${p("section", "<topic>")} for more detail:")
        appendLine("  ${p("section", "install")}          — snippet to paste into AGENTS.md / CLAUDE.md")
        appendLine("  ${p("section", "workflow")}         — step-by-step analysis workflow")
        appendLine("  ${p("section", "interpretation")}   — heuristics for reading results")
        appendLine("  ${p("section", "schemas")}          — JSON output schemas per task")
        appendLine("  ${p("section", "extraction")}       — extracting output, jq examples")
        appendLine("Run ${u("help")} for full parameter documentation.")
    }

    private val HELP_GOALS = setOf("help", "agent-help", "config-help")
    private val FORMAT_PARAM_NAMES = setOf("format", "llm")

    private fun StringBuilder.appendTaskReference(tool: BuildTool) {
        val compilationTasks = TaskRegistry.ALL_TASKS
            .filter { it.requiresCompilation && it.goal !in HELP_GOALS }
        val gitTasks = TaskRegistry.ALL_TASKS
            .filter { !it.requiresCompilation && it.goal !in HELP_GOALS }
        val helpTasks = TaskRegistry.ALL_TASKS
            .filter { it.goal in HELP_GOALS }

        for (task in compilationTasks) {
            appendTaskLine(task, tool)
        }
        appendLine()
        appendLine("  Git history (no compilation needed):")
        for (task in gitTasks) {
            appendTaskLine(task, tool)
        }
        appendLine()
        appendLine("  Help:")
        for (task in helpTasks) {
            appendLine("  ${padTo(task.taskName(tool), 22)}${task.description}")
        }
    }

    private fun StringBuilder.appendTaskLine(task: TaskDef, tool: BuildTool) {
        val taskName = task.taskName(tool)
        val nonFormatParams = task.params.filter { it.name !in FORMAT_PARAM_NAMES }
        val paramStr = nonFormatParams.joinToString(" ") { param ->
            val rendered = param.render(tool)
            if (param.defaultValue != null) "[$rendered]" else rendered
        }
        val line = if (paramStr.isEmpty()) {
            "  ${padTo(taskName, 22)}${task.description}"
        } else {
            "  ${padTo(taskName, 22)}${padTo(task.description, 36)}$paramStr"
        }
        appendLine(line)
    }

    private fun StringBuilder.appendGlobalParameters(tool: BuildTool) {
        appendLine("  ${padTo(TaskRegistry.LLM.render(tool), 28)}Compact, token-efficient output (all tasks)")
        appendLine("  ${padTo(TaskRegistry.FORMAT.render(tool), 28)}Machine-readable JSON output (all tasks)")
        appendLine()

        val tasksByParam = buildMap<String, MutableList<TaskDef>> {
            for (task in TaskRegistry.ALL_TASKS) {
                for (param in task.params) {
                    if (param.name !in FORMAT_PARAM_NAMES) {
                        getOrPut(param.name) { mutableListOf() }.add(task)
                    }
                }
            }
        }

        val allParams = TaskRegistry.ALL_TASKS
            .flatMap { it.params }
            .filter { it.name !in FORMAT_PARAM_NAMES }
            .distinctBy { it.name }

        for (param in allParams) {
            val tasks = tasksByParam[param.name] ?: continue
            val rendered = param.render(tool)
            val taskList = tasks.joinToString(", ") { it.taskName(tool) }
            val defaultSuffix = param.defaultValue?.let { ", default: $it" } ?: ""
            appendLine("  ${padTo(rendered, 28)}${param.description} ($taskList$defaultSuffix)")
        }
    }

    private fun padTo(s: String, width: Int): String =
        if (s.length >= width) "$s " else s.padEnd(width)
}
