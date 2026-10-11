package no.f12.codenavigator

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskDef
import no.f12.codenavigator.registry.TaskRegistry

/**
 * Agent-facing help in three layers of progressive disclosure:
 *
 * 1. `install`  — a short snippet for CLAUDE.md / AGENTS.md: rules and a pointer, nothing task-specific.
 * 2. default    — the guide: how to think with cnav, plus a one-line-per-task directory.
 * 3. `task=<goal>` and the sections — full detail on demand, generated from [HelpRender].
 *
 * `skill` renders the guide as a Claude Code / Cursor skill file. Topics are philosophy guides.
 */
object AgentHelpText {
    val SECTIONS = listOf("install", "setup", "workflow", "questions", "interpretation", "schemas", "extraction", "recommendations", "refactor", "getting-started", "skill")
    val TOPICS = listOf("hexagonal", "tttd", "fakes", "manual-di")

    fun generate(tool: BuildTool = BuildTool.GRADLE, section: String? = null, topic: String? = null, task: String? = null): String {
        if (task != null) return generateTask(tool, task)
        if (topic != null) return generateTopic(tool, topic)
        return when (section) {
            null -> generateGuide(tool)
            "install" -> generateInstall(tool)
            "setup" -> generateSetup(tool)
            "workflow" -> generateWorkflow(tool)
            "questions" -> "# code-navigator: Which task answers which question\n\n" + HelpRender.questionIndex(tool)
            "interpretation" -> "# code-navigator: Reading the results\n\n" + HelpRender.interpretation(tool)
            "schemas" -> generateSchemas(tool)
            "extraction" -> generateExtraction(tool)
            "recommendations" -> generateRecommendations(tool)
            "getting-started" -> generateGettingStarted(tool)
            "refactor" -> generateRefactor(tool)
            "skill" -> generateSkill(tool)
            else -> "Unknown section: '$section'. Valid sections: ${SECTIONS.joinToString(", ")}"
        }
    }

    // ------------------------------------------------------------------ layer 3: one task

    private fun generateTask(tool: BuildTool, goal: String): String {
        val task = TaskRegistry.ALL_TASKS.firstOrNull { it.goal == goal || it.taskName(tool).equals(goal, ignoreCase = true) || it.aliases.contains(goal) }
            ?: return "Unknown task: '$goal'. Valid goals: ${TaskRegistry.ALL_TASKS.joinToString(", ") { it.goal }}"
        return HelpRender.taskBlock(task, tool, full = true).trimEnd()
    }

    // ------------------------------------------------------------------ layer 1: install snippet

    private fun generateInstall(tool: BuildTool): String = buildString {
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)
        val plugin = when (tool) {
            BuildTool.GRADLE -> "Gradle plugin `no.f12.code-navigator`"
            BuildTool.MAVEN -> "Maven plugin `code-navigator-maven-plugin`"
        }
        appendLine("## Code Navigator (cnav)")
        appendLine()
        appendLine("$plugin: code navigation, architecture analysis and deterministic refactoring from compiled bytecode, git history and source.")
        appendLine("Before first use in a session, run ${u("agent-help")} and follow it. ${u("agent-help", p("task", "<goal>"))} documents one task.")
        appendLine()
        appendLine("Rules:")
        appendLine("- NEVER use grep, ripgrep, Glob, Read or an explore agent to find type/method/field references. Use cnav: bytecode analysis sees same-package references, extension functions, companion objects and lambdas that grep misses. grep is only for string literals, comments, file names and Kotlin `inline` functions.")
        appendLine("- ALWAYS pass ${p("format", "llm")}. Read everything between ---CNAV_BEGIN--- and ---CNAV_END---; never pipe through head, tail or grep.")
        appendLine("- Rename, move and delete with cnav (${tool.taskName("move-class")}, ${tool.taskName("rename-method")}, ${tool.taskName("safe-delete")}), ${tool.paramFlag("preview")} first. Never edit imports by hand afterwards.")
        appendLine()
        appendLine("Start here:")
        appendLine("- Who uses type X:                 ${u("find-usages", p("type", "X"))}")
        appendLine("- Who calls method X:              ${u("find-callers", p("pattern", "X"))}")
        appendLine("- Exact lines of a declaration:    ${u("find-declaration", p("pattern", "Class.method"))}")
        appendLine("- Everything about class X:        ${u("context", p("pattern", "X"))}")
        appendLine("- Architecture health:             ${u("cycles")}, ${u("rings")}, ${u("converge")}")
    }

    // ------------------------------------------------------------------ layer 2: the guide

    private fun generateGuide(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        val pf = { name: String -> tool.paramFlag(name) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)

        appendLine("# code-navigator: Agent Guide")
        appendLine()
        appendLine("code-navigator (cnav) answers structural questions about a JVM codebase from compiled bytecode, git history and source,")
        appendLine("and performs deterministic refactorings. One call returns a complete, correct answer; no iterative searching.")
        appendLine()
        appendLine("## Rules")
        appendLine()
        appendLine("- NEVER use grep, ripgrep, Glob or Read to find type/method/field references, and never spawn an explore agent for it.")
        appendLine("  Bytecode analysis sees implicit same-package references, extension functions, companion objects and lambdas that grep misses.")
        appendLine("  grep is right for: string literals, comments, log messages, file names, stale references after a delete (bytecode is gone),")
        appendLine("  and Kotlin `inline` functions (inlining leaves no call edge; ${t("find-callers")} warns when this applies).")
        appendLine("- ALWAYS pass ${p("format", "llm")}. It is compact and wraps results in ---CNAV_BEGIN--- / ---CNAV_END--- markers that separate them")
        appendLine("  from build-tool noise. Everything between the markers is the result; ignore the rest. ${p("format", "json")} gives structured output.")
        appendLine("- NEVER pipe cnav output through head, tail or grep. It is short and specific; truncating it causes retries. If it is unexpectedly long, write it to a file.")
        appendLine("- Bytecode tasks need compiled classes. If cnav warns about stale class files, rebuild. If compilation is broken mid-refactoring, cnav still works against the last successful build.")
        appendLine("- Empty results come with hints ({\"results\":[],\"hints\":[...]}); read the hints before changing the query.")
        appendLine()
        appendLine("## Which task")
        appendLine()
        appendLine("- \"Who uses type X?\"                        → ${u("find-usages", p("type", "X"))}  (collapsed by default; ${p("group-by", "file")} for a summary, ${pf("raw")} for bytecode detail)")
        appendLine("- \"Where is field X read or written?\"       → ${u("find-usages", p("owner-class", "com.example.Config"), p("field", "timeout"))}  (${p("field", "x")} alone for a top-level property)")
        appendLine("- \"Who calls method X?\" / call hierarchy    → ${u("find-callers", p("pattern", "Class.method"))}  (${p("maxdepth", "3")} adjustable, through interfaces and inheritance)")
        appendLine("- \"What does X call?\"                       → ${u("find-callees", p("pattern", "Class.method"))}")
        appendLine("- \"Who implements X?\" / port→adapter map    → ${u("find-interfaces", p("pattern", "X"))}  (${p("scope", "test")} shows only test fakes)")
        appendLine("- \"What does class X look like?\"            → ${u("class-detail", p("pattern", "X"))}; the full picture in one call: ${u("context", p("pattern", "X"))}")
        appendLine("- \"Where exactly is X.m? Which lines?\"      → ${u("find-declaration", p("pattern", "X.m"))}  (PSI-exact range incl. KDoc, no compile needed; read or replace exactly that range)")
        appendLine("- \"All methods of X with line ranges\"       → ${u("find-declaration", p("pattern", "\"X\\\\..*\""), p("kind", "function"))}")
        appendLine("- \"Why does package A depend on B?\"         → ${u("why-depends", p("from-package", "A"), p("to-package", "B"))}")
        appendLine("- \"Is the architecture healthy?\"            → ${u("cycles")}, ${u("rings")}, ${u("converge")}; ${u("report")} for everything at once")
        appendLine("- \"Where should I start refactoring?\"       → ${u("converge")} (structural problems that also cost change effort), then ${t("hotspots")} + ${t("complexity")}")
        appendLine("- \"What is the blast radius of my change?\"  → ${u("changed-since", p("ref", "main"))}")
        appendLine()
        appendLine("The full question index: ${u("agent-help", p("section", "questions"))}. One task in depth: ${u("agent-help", p("task", "<goal>"))}.")
        appendLine()
        appendLine("## Refactoring protocol")
        appendLine()
        appendLine("1. Before renaming or moving: run the command with ${pf("preview")}. With ${p("format", "llm")} the preview is a unified diff of every change.")
        appendLine("2. Apply by re-running without ${pf("preview")}. The diff comes back with the result, so you know the state of every affected file without re-reading it.")
        appendLine("   ${p("format", "diff")} prints a raw unified diff with no markers. Do not edit imports by hand afterwards; the commands handle same-package references.")
        appendLine("3. Before deleting: ${u("find-usages", p("type", "X"))}, or let ${u("safe-delete", p("target-class", "com.example.X"))} refuse when something still uses it.")
        appendLine("4. After deleting: grep for stale string references; bytecode cannot see a class that no longer compiles.")
        appendLine("5. Verify: ${u("cycles")} for new tangles, ${u("metrics")} against the baseline you took first.")
        appendLine("For several moves, write a plan file and simulate it with ${t("cycles")} ${p("plan-file", "plan.json")} before ${t("execute-plan")} applies it.")
        appendLine()
        appendLine("Refactoring commands (I want to...):")
        for (task in TaskRegistry.REFACTORING_TASKS) {
            appendLine("  ${(task.intent!!).padEnd(44)} ${HelpRender.firstUsage(task, tool)}")
        }
        appendLine()
        appendLine("## Patterns")
        appendLine()
        appendLine(HelpRender.patternMatching(tool))
        appendLine()
        appendLine("## Task directory")
        appendLine()
        appendLine(HelpRender.directory(tool, withQuestion = true))
        appendLine()
        appendLine("## More")
        appendLine()
        appendLine("${u("agent-help", p("task", "<goal>"))}         full documentation for one task: params, examples, JSON schema, how to read results")
        appendLine("${u("agent-help", p("section", "<name>"))}     ${SECTIONS.joinToString(", ")}")
        appendLine("${u("agent-help", p("topic", "<name>"))}       ${TOPICS.joinToString(", ")} (philosophy guides)")
        appendLine("${u("help")}                               compact listing of every task and its options")
        appendLine()
        appendLine(HelpRender.feedback())
    }

    // ------------------------------------------------------------------ sections

    private fun generateSetup(tool: BuildTool): String = buildString {
        appendLine("# code-navigator: Setup")
        appendLine()
        appendLine("## Permissions")
        appendLine()
        appendLine("If a cnav command triggers a Bash approval prompt, create a permission rule so future cnav commands run without prompting:")
        appendLine("1. Copy the command string shown in the approval prompt")
        appendLine("2. Keep everything up to and including `cnav`, replace the rest with `*`")
        appendLine("3. Add as a Bash allow rule in `.claude/settings.local.json`")
        appendLine()
        when (tool) {
            BuildTool.GRADLE -> {
                appendLine("Example: if the prompt shows `./gradlew cnavListClasses --format=llm`, add `\"Bash(./gradlew cnav*)\"`.")
                appendLine("If the command has a preamble (e.g. `eval \"\$(mise activate bash)\" && ./gradlew ...`), include it: `\"Bash(eval \\\"\\$(mise activate bash)\\\" && ./gradlew cnav*)\"`.")
            }
            BuildTool.MAVEN -> appendLine("Example: if the prompt shows `mvn cnav:find-usages -Dtype=Foo -Dformat=llm`, add `\"Bash(mvn cnav:*)\"`.")
        }
        appendLine()
        appendLine("## cnav-config.json")
        appendLine()
        appendLine("A \"defaults\" section commits shared task options to the repo: { \"defaults\": { \"format\": \"llm\", \"scope\": \"prod\" } }")
        appendLine("Precedence: explicit CLI option > cnav-config.json > the task's own default. The same file holds ring-classifier hints for ${tool.taskName("rings")}.")
        appendLine()
        appendLine("## Troubleshooting")
        appendLine()
        appendLine("If bytecode-based tasks fail with compilation or cache errors:")
        when (tool) {
            BuildTool.GRADLE -> {
                appendLine("  1. Stop the daemon:    ./gradlew --stop")
                appendLine("  2. Clear Kotlin cache: rm -rf build/kotlin")
                appendLine("  3. Retry with clean:   ./gradlew clean <task>")
            }
            BuildTool.MAVEN -> {
                appendLine("  1. Clear Kotlin cache: rm -rf target/kotlin-ic")
                appendLine("  2. Retry with clean:   mvn clean <goal>")
            }
        }
    }

    private fun generateWorkflow(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        val pf = { name: String -> tool.paramFlag(name) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)

        appendLine("# code-navigator: Recommended Workflow")
        appendLine()
        appendLine("## 1. Orient")
        appendLine("   ${u("package-deps")}                 # package dependency map")
        appendLine("   ${u("dsm")}                          # dependency structure matrix")
        appendLine("   ${u("list-classes")}                 # all classes and source files")
        appendLine()
        appendLine("## 2. Find")
        appendLine("   ${u("find-class", p("pattern", "Service"))}")
        appendLine("   ${u("find-symbol", p("pattern", "reset"))}")
        appendLine("   ${u("find-declaration", p("pattern", "Service.reset"))}   # exact line/offset boundaries, no compile needed")
        appendLine("   ${u("find-declaration", p("pattern", "\"Service\\\\..*\""), p("kind", "function"))}   # every method of a class with boundaries")
        appendLine("   ${u("find-class", p("pattern", "Client"), p("jar", "com.example:http-client"))}   # inside a dependency JAR")
        appendLine()
        appendLine("## 3. Inspect")
        appendLine("   ${u("class-detail", p("pattern", "ResetPasswordService"))}")
        appendLine("   ${u("context", p("pattern", "ResetPasswordService"))}   # detail + callers + callees + interfaces in one call")
        appendLine()
        appendLine("## 4. Trace")
        appendLine("   ${u("find-callers", p("pattern", "resetPassword"), p("maxdepth", "3"), p("project-only", "true"))}")
        appendLine("   ${u("find-callees", p("pattern", "handleRequest"), p("maxdepth", "3"), p("project-only", "false"))}")
        appendLine()
        appendLine("## 5. Map abstractions")
        appendLine("   ${u("find-interfaces", p("pattern", "Repository"))}   # test fakes included; ${p("scope", "test")} for fakes only")
        appendLine("   ${u("type-hierarchy", p("pattern", "Service"))}")
        appendLine()
        appendLine("## 6. Migrate external APIs")
        appendLine("   ${u("find-usages", p("owner-class", "kotlinx.datetime.LocalDate"), p("method", "getMonthNumber"))}")
        appendLine("   ${u("find-usages", p("owner-class", "com.example.Config"), p("field", "timeout"))}")
        appendLine("   ${u("find-usages", p("type", "kotlinx.datetime.Instant"))}")
        appendLine()
        appendLine("## 7. Behaviour from git history (no compilation)")
        appendLine("   ${u("hotspots")}   ${u("coupling")}   ${u("code-age")}   ${u("authors")}   ${u("churn")}   ${u("volatility")}")
        appendLine("   All accept ${p("after", "YYYY-MM-DD")} (default: 1 year ago).")
        appendLine()
        appendLine("## 8. Source scans (no compilation)")
        appendLine("   ${u("size", p("over", "100"))}   ${u("duplicates", p("scope", "prod"))}   ${u("duplicates", p("min-tokens", "100"))}")
        appendLine()
        appendLine("## 9. Dead code")
        appendLine("   ${u("dead")}   ${u("dead", p("scope", "prod"))}   ${u("dead", p("exclude", "\"com\\\\.example\\\\.grpc\""))}   ${u("dead", p("treat-as-dead", "spring"))}")
        appendLine("   ${u("dead", p("format", "json"))} > dead-baseline.json, later ${u("dead", p("baseline", "dead-baseline.json"))}   # removed/remaining/new")
        appendLine()
        appendLine("## 10. Structure and coupling")
        appendLine("   ${u("metrics")}   ${u("complexity")}   ${u("class-metrics")}   ${u("rank")}")
        appendLine("   ${u("distance")}   ${u("strength")}   ${u("volatility")}   ${u("balance")}   # Balanced Coupling: strength × distance × volatility")
        appendLine("   ${u("cohesion")}   ${u("move-suggest")}   ${u("suggest-structure")}   ${u("type-affinity")}")
        appendLine("   ${u("rings")}   ${u("ambient")}   ${u("test-coupling")}   ${u("test-alignment")}   ${u("converge")}")
        appendLine()
        appendLine("## 11. Impact")
        appendLine("   ${u("changed-since", p("ref", "main"))}   ${u("changed-since", p("ref", "v1.2.0"))}   ${u("changed-since", p("ref", "HEAD~5"))}")
        appendLine()
        appendLine("## Useful combinations")
        appendLine("  Refactoring targets:   ${t("hotspots")} + ${t("complexity")} + ${t("cycles")}  (or ${t("converge")}, which intersects them)")
        appendLine("  Architecture overview: ${t("dsm")} + ${t("package-deps")} + ${t("cycles")}")
        appendLine("  Change risk:           ${t("changed-since")} + ${t("find-callers")}")
        appendLine("  Code review context:   ${t("hotspots")} + ${t("authors")} + ${t("churn")}")
        appendLine("  Coupling health:       ${t("balance")}")
        appendLine()
        appendLine("## Iterative refactoring with ${pf("plan-file")}")
        appendLine("  1. ${u("cycles")}")
        appendLine("  2. plan.json: [{\"action\":\"move\",\"type\":\"com.example.api.CacheConfig\",\"to\":\"com.example.infra\"}]")
        appendLine("  3. ${u("cycles", p("plan-file", "plan.json"))}   # simulated; repeat 2-3 until satisfied")
        appendLine("  4. ${u("execute-plan", p("plan-file", "plan.json"), pf("preview"))}")
        appendLine("  5. ${u("execute-plan", p("plan-file", "plan.json"))}, recompile, re-run step 1")
        appendLine("  ${pf("plan-file")} on analysis tasks simulates; on ${t("execute-plan")} it changes files. Class names are resolved to FQCNs before any step runs.")
        appendLine()
        appendLine("## CI enforcement")
        appendLine("  ${u("cycles", p("scope", "prod"), pf("fail-on-violation"), p("max-cycles", "0"))}")
        appendLine("  ${u("rings", p("scope", "prod"), pf("fail-on-violation"), p("max-violations", "0"))}")
        appendLine("  Raise the threshold to allow a known baseline, then ratchet it down as violations are fixed. Also on ${t("ambient")}, ${t("test-coupling")}, ${t("test-alignment")}.")
    }

    private fun generateSchemas(tool: BuildTool): String = buildString {
        appendLine("# code-navigator: JSON Schemas (${tool.param("format", "json")})")
        appendLine()
        appendLine(HelpRender.schemas(tool))
        appendLine()
        appendLine("Empty results:")
        appendLine("  {\"results\":[],\"hints\":[\"Try ${tool.param("methods", "true")} to include method-level annotations\"]}")
        appendLine("  hints holds actionable suggestions and may be empty. TEXT output appends the hints as plain lines.")
    }

    private fun generateExtraction(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)
        appendLine("# code-navigator: Extracting Output")
        appendLine()
        appendLine("With ${p("format", "llm")} or ${p("format", "json")} the build tool mixes its own output into stdout, so results are wrapped:")
        appendLine("  ---CNAV_BEGIN---")
        appendLine("  [{\"className\":\"...\"}]")
        appendLine("  ---CNAV_END---")
        appendLine()
        appendLine("Extract the JSON:  ${u("list-classes", p("format", "json"))} | sed -n '/CNAV_BEGIN/,/CNAV_END/p'")
        appendLine("Shell helper:      cnav() { ${tool.command} \"\$@\" | sed -n '/CNAV_BEGIN/,/CNAV_END/p'; }")
        appendLine()
        appendLine("## jq examples (with the helper)")
        appendLine("  cnav ${t("list-classes")} ${p("format", "json")} | jq '.[].className'")
        appendLine("  cnav ${t("find-class")} ${p("pattern", "Service")} ${p("format", "json")} | jq '.[].sourcePath'")
        appendLine("  cnav ${t("find-callers")} ${p("pattern", "save")} ${p("maxdepth", "3")} ${p("format", "json")} | jq '.[].children[].method'")
        appendLine("  cnav ${t("package-deps")} ${p("package", "domain")} ${p("reverse", "true")} ${p("format", "json")} | jq '.[].dependents[]'")
        appendLine("  cnav ${t("find-interfaces")} ${p("pattern", "Repository")} ${p("format", "json")} | jq '.[].implementors[].className'")
        appendLine("  cnav ${t("find-declaration")} ${p("pattern", "Service.save")} ${p("format", "json")} | jq '.[] | [.startLine, .endLine]'")
    }

    private fun generateRecommendations(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        appendLine("# code-navigator: Recommendations")
        appendLine()
        appendLine("Practices that make structural analysis more effective over time. None are enforced by the plugin.")
        appendLine()
        appendLine("## Code coverage")
        appendLine("Set up JaCoCo (or equivalent). Coverage says which code paths tests exercise; ${t("rank")} and ${t("complexity")} say which are structurally important.")
        appendLine("Together they reveal the highest-risk gaps: important code with low coverage. Without coverage, dead code removal and refactoring carry avoidable risk.")
        appendLine()
        appendLine("## Test-driven development")
        appendLine("Code written test-first is testable by design, which makes the analysis trustworthy: ${t("dead")} TEST_ONLY items are real signals, and with fakes at the")
        appendLine("boundaries ${t("find-callers")} and ${t("find-interfaces")} show real dependency structure rather than mock wiring. Small focused classes show up as healthy fan-in/fan-out.")
        appendLine()
        appendLine("## Analysis cadence")
        appendLine("Run ${t("cycles")} after adding package dependencies, ${t("dead")} after removing features, ${t("balance")} periodically, ${t("hotspots")} + ${t("complexity")} before planning refactoring work.")
        appendLine("Trends matter more than absolute numbers.")
        appendLine()
        appendLine("## Commit discipline")
        appendLine("Keep structural changes (renames, moves, extractions) in separate commits from behavioural ones. Git-history tasks (${t("hotspots")}, ${t("coupling")}, ${t("churn")}) stay accurate,")
        appendLine("and each commit is independently verifiable: structural commits pass existing tests with no new behaviour, behavioural commits carry new tests.")
        appendLine()
        appendLine("## Architecture conformance")
        appendLine("${t("rings")} detects hexagonal rings from the dependency graph with no configuration and reports violations at analysis time rather than code-review time. Run it in CI.")
    }

    private fun generateGettingStarted(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)
        appendLine("# code-navigator: Getting Started — Improving Code Quality")
        appendLine()
        appendLine("code-navigator is opinionated: it measures and guides codebases toward hexagonal architecture (ports & adapters).")
        appendLine("The analysis tasks quantify how well code separates domain logic from infrastructure, and where coupling crosses boundaries it should not.")
        appendLine()
        appendLine("## Step 1: Assess")
        appendLine("  ${u("metrics")}                 # snapshot: cycles, dead code, coupling stats")
        appendLine("  ${u("cycles")}                  # circular package dependencies, the #1 structural problem")
        appendLine("  ${u("cohesion", p("min-edges", "10"))}   # which packages should be split")
        appendLine("  ${u("converge")}                # where structure and change effort agree")
        appendLine()
        appendLine("## Step 2: Understand coupling quality")
        appendLine("  ${u("strength")}   # CONTRACT (good), MODEL (ok), FUNCTIONAL (review)")
        appendLine("  ${u("distance")}   # how far apart coupled packages sit")
        appendLine("  ${u("balance")}    # combined verdict per package pair")
        appendLine("  In hexagonal terms: Domain→Port should be CONTRACT; Adapter→Port CONTRACT or MODEL; Adapter→Domain and Domain→Adapter should not exist.")
        appendLine()
        appendLine("## Step 3: Enforce")
        appendLine("  ${u("rings")}   # rings detected from dependency inversions; dependencies may only point inward")
        appendLine("  Ring 0 domain model, then ports/services, adapters, and the composition root outermost. Violations list the class-level edges that break the rule.")
        appendLine()
        appendLine("## Step 4: Act on findings")
        appendLine("  Cycle between A and B           → ${u("why-depends", p("from-package", "A"), p("to-package", "B"))}, then extract a port in a shared package")
        appendLine("  Low cohesion (REVIEW)           → inspect the worst packages; split domain from infrastructure sub-packages")
        appendLine("  FUNCTIONAL where CONTRACT due   → the edge goes through a concrete class; extract an interface and depend on that")
        appendLine("  Adapter referenced by domain    → introduce a port in the domain that the adapter implements")
        appendLine()
        appendLine("## What good looks like")
        appendLine("  ${t("cycles")}: none (or only test infrastructure)   ${t("cohesion")}: domain COHESIVE, ports THIN_LAYER   ${t("strength")}: domain edges CONTRACT")
        appendLine("  ${t("balance")}: no DANGER   ${t("rings")}: zero violations")
    }

    private fun generateRefactor(tool: BuildTool): String = buildString {
        val p = { name: String, value: String -> tool.param(name, value) }
        val pf = { name: String -> tool.paramFlag(name) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)
        fun lookup(goals: List<String>) {
            for (goal in goals) {
                val task = TaskRegistry.ALL_TASKS.first { it.goal == goal }
                val q = task.doc.whenToUse.firstOrNull()?.let { HelpRender.r(it, tool) } ?: task.description
                appendLine("  ${q.padEnd(52)} → ${HelpRender.firstUsage(task, tool)}")
            }
        }
        appendLine("# code-navigator: Refactoring Task Lookup")
        appendLine()
        appendLine("## I want to...")
        for (task in TaskRegistry.REFACTORING_TASKS) {
            appendLine("  ${(task.intent!!).padEnd(52)} → ${HelpRender.firstUsage(task, tool)}")
        }
        appendLine()
        appendLine("  All write commands support ${pf("preview")}. With ${p("format", "llm")} the preview is a unified diff; ${p("format", "diff")} prints it raw.")
        appendLine("  Without preview, changes are applied and the diff is returned. NEVER edit imports by hand afterwards.")
        appendLine()
        appendLine("## Explore before refactoring")
        lookup(listOf("find-usages", "find-callers", "find-callees", "class-detail", "find-declaration", "type-hierarchy", "find-interfaces"))
        appendLine()
        appendLine("## Find targets")
        lookup(listOf("converge", "dead", "duplicates", "complexity", "hotspots", "cycles", "move-suggest"))
        appendLine()
        appendLine("## Verify afterwards")
        appendLine("  Remaining references                                 → ${u("find-usages", p("type", "OldName"))}")
        appendLine("  No new cycles                                        → ${u("cycles")}")
        appendLine("  Compare metrics                                      → ${u("metrics")}")
    }

    // ------------------------------------------------------------------ skill

    private fun generateSkill(tool: BuildTool): String = buildString {
        val dir = ".claude/skills/code-navigator"
        appendLine("Save the text below the line as $dir/SKILL.md (Claude Code) or adapt the frontmatter for your agent's skill format.")
        appendLine("Regenerate it after upgrading code-navigator; everything in it is derived from the installed version.")
        appendLine("----------------------------------------------------------------------")
        appendLine("---")
        appendLine("name: code-navigator")
        appendLine("description: Navigate, analyse and refactor this JVM codebase with code-navigator (cnav) instead of grep. Use whenever asked who uses or calls something, where a declaration is, what a class looks like, whether the architecture has cycles or layer violations, or before renaming, moving or deleting code. Runs via ${tool.command}.")
        appendLine("---")
        appendLine()
        append(generateGuide(tool))
    }

    // ------------------------------------------------------------------ topics

    private fun generateTopic(tool: BuildTool, topic: String): String = when (topic) {
        "hexagonal" -> generateTopicHexagonal(tool)
        "tttd" -> generateTopicTttd(tool)
        "fakes" -> generateTopicFakes(tool)
        "manual-di" -> generateTopicManualDi(tool)
        else -> "Unknown topic: '$topic'. Valid topics: ${TOPICS.joinToString(", ")}"
    }

    private fun generateTopicHexagonal(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)
        appendLine("# Topic: Hexagonal Architecture (Ports & Adapters)")
        appendLine()
        appendLine("## Philosophy")
        appendLine("Dependencies flow INWARD only. The domain never depends on infrastructure. External I/O (HTTP, database, messaging) lives in adapters at the outer ring; the domain defines ports (interfaces) that adapters implement.")
        appendLine("The key question: \"Does it do I/O or depend on something that does?\" Yes → adapter (outside). No → domain or application service (inside).")
        appendLine("${t("rings")} implements exactly that rule. A ring boundary is a dependency inversion: a port owned by the inside, implemented by a class that does I/O. Rings are counted from those boundaries, so the count is emergent: 2 or 3 for most services, 1 for code with no inversion at all, reported as \"no hexagon here\" rather than a made-up ladder.")
        appendLine()
        appendLine("## How code-navigator supports this")
        appendLine("  ${t("rings")}            detects rings from dependency inversions and reports OUTWARD violations only; same-ring classes are peers. ${p("scope", "prod")} excludes test source sets.")
        appendLine("  ${t("ambient")}          domain classes reading the clock, randomness or environment directly, which no direction check can see.")
        appendLine("  ${t("cycles")}           package cycles often indicate layer violations.")
        appendLine("  ${t("strength")}         CONTRACT deps are healthy (through interfaces); FUNCTIONAL deps bypass ports.")
        appendLine("  ${t("balance")}          strength × distance × volatility verdict per package pair.")
        appendLine("  ${t("find-interfaces")}  port→adapter mapping.")
        appendLine()
        appendLine("## Interpreting results")
        appendLine("  OUTWARD violation in ${t("rings")}: an inner class depends on an outer one. Extract a port in the inner ring; move the concrete class outward.")
        appendLine("  \"1 ring, no inversion boundaries\": nothing inverts a dependency yet. Find a class that does I/O, extract the interface its callers need, have the I/O class implement it.")
        appendLine("  FUNCTIONAL where CONTRACT expected (${t("strength")}): introduce a port interface in the depended-upon package.")
        appendLine("  Cycle between domain and infrastructure (${t("cycles")}): invert the dependency through a port.")
        appendLine()
        appendLine("## What good looks like")
        appendLine("  ${t("rings")}: 2+ rings, zero violations   ${t("strength")}: Domain→Port all CONTRACT   ${t("cycles")}: none across layers   ${t("cohesion")}: domain COHESIVE, ports THIN_LAYER")
        appendLine()
        appendLine("## Actions when violations are found")
        appendLine("1. ${u("why-depends", p("from-package", "<inner>"), p("to-package", "<outer>"))} to find the class edges")
        appendLine("2. Extract an interface in the inner package for what the inner ring needs")
        appendLine("3. Move the concrete implementation to the outer ring")
        appendLine("4. Wire via constructor parameter")
        appendLine("5. Re-run ${t("rings")}")
    }

    private fun generateTopicTttd(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)
        appendLine("# Topic: Testing Through The Domain (TTTD)")
        appendLine()
        appendLine("## Philosophy")
        appendLine("Tests should exercise behaviour through the domain layer, not by calling infrastructure ports (repositories, clients) directly. A test that sets up state with repository.save() or verifies with repository.findAll() bypasses the domain's invariants and couples itself to infrastructure contracts.")
        appendLine("Instead: set up state through application or domain services (the entry points production uses), verify through the domain's public API, and use in-memory fakes for ports, which already maintain state. Tests then validate the same paths as production, and swapping a database or client does not break them.")
        appendLine()
        appendLine("## How code-navigator supports this")
        appendLine("  ${t("test-coupling")}  detects tests that call port methods directly. ${p("ports", "\".*Repository|.*Client\"")} names the ports, ${p("exclude", "\".*IntegrationTest\"")} skips intentional integration tests, ${p("detail", "true")} shows the port methods called.")
        appendLine()
        appendLine("## Interpreting results")
        appendLine("  Each finding shows the test class, the port it calls, a confidence score, and whether it is an adapter test (acceptable).")
        appendLine("  High confidence: a service test calling repository.save() to set up state. Fix: call the service method instead (userService.register()).")
        appendLine("  Acceptable: adapter tests, explicitly marked integration tests, test infrastructure that uses ports internally.")
        appendLine()
        appendLine("## Actions")
        appendLine("1. Identify what the test sets up or verifies")
        appendLine("2. Find the service method that achieves the same thing; if none exists, that is a domain gap, add it")
        appendLine("3. Replace direct port calls with service calls")
        appendLine("4. Re-run ${u("test-coupling", p("ports", "\".*Repository\""))}")
    }

    private fun generateTopicFakes(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)
        appendLine("# Topic: Fakes Over Mocks")
        appendLine()
        appendLine("## Philosophy")
        appendLine("Use in-memory implementations (fakes) instead of mocking frameworks. A fake implements the port with simple state (HashMap, MutableList) instead of real I/O.")
        appendLine("Fakes enforce the port contract, do not encode call-order expectations (tests stay refactoring-safe), support TTTD because they hold state, and are reusable across tests.")
        appendLine("  interface UserRepository { fun save(user: User); fun findById(id: UserId): User? }")
        appendLine("  class FakeUserRepository : UserRepository { private val store = mutableMapOf<UserId, User>(); override fun save(user: User) { store[user.id] = user }; override fun findById(id: UserId) = store[id] }")
        appendLine()
        appendLine("## How code-navigator supports this")
        appendLine("  ${t("find-interfaces")}  all implementations of a port; ${p("scope", "test")} shows only test fakes. A port with no test fake is a gap.")
        appendLine("  ${t("test-coupling")}   violations often mean a missing fake: tests call ports directly because nothing else holds state.")
        appendLine("  ${t("find-usages")}     ${u("find-usages", p("type", "MockK"))} finds where a mock library is used.")
        appendLine()
        appendLine("## Actions")
        appendLine("1. ${u("find-interfaces", p("pattern", "Repository|Client|Gateway"))} to inventory ports")
        appendLine("2. Create a fake for each port without one")
        appendLine("3. Wire fakes in the TestContext (manual DI)")
        appendLine("4. Replace mock() calls with the fake from the TestContext")
        appendLine("5. ${u("test-coupling", p("ports", "\".*Repository|.*Client\""))}; violations should decrease")
    }

    private fun generateTopicManualDi(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }
        fun u(goal: String, vararg params: String) = tool.usage(goal, *params)
        appendLine("# Topic: Manual Dependency Injection")
        appendLine()
        appendLine("## Philosophy")
        appendLine("Wire dependencies explicitly in plain code: no framework, annotations or reflection. Define an AppDependencies interface and implement it twice, SystemContext (production: real adapters) and SystemTestContext (test: fakes), independently, with no inheritance between them. Tests get concrete fake types through covariant overrides, no casting.")
        appendLine("This complements hexagonal architecture (ports are the contract), fakes (wired via TestContext) and TTTD (tests go through services wired in TestContext).")
        appendLine()
        appendLine("## How code-navigator supports this")
        appendLine("  ${t("find-interfaces")}  each port should have one real adapter and one test fake; ${p("scope", "test")} shows the test side.")
        appendLine("  ${t("annotations")}      ${u("annotations", p("pattern", "Inject|Autowired|Component"))} inventories framework DI.")
        appendLine("  ${t("find-usages")}      ${u("find-usages", p("type", "Inject"))} finds injection points; service-locator calls (getBean, resolve) the same way.")
        appendLine("  ${t("find-callers")}     how a service is obtained; callers going through a container are migration candidates.")
        appendLine("  ${t("complexity")}       high fan-out on a context class is expected (it wires everything); on a service it means too many deps.")
        appendLine("  ${t("rings")}            a context sits in the outermost ring and nothing inside should import it; if domain code does, that is a service locator.")
        appendLine()
        appendLine("## What good looks like")
        appendLine("  ${t("annotations")}: no @Inject/@Autowired/@Component   ${t("find-interfaces")}: every port has 1 adapter + 1 fake   ${t("rings")}: context outermost, nothing depends on it")
        appendLine()
        appendLine("## Actions")
        appendLine("1. ${u("annotations", p("pattern", "Inject|Autowired|Component"))} to inventory framework DI usage")
        appendLine("2. For each annotated class, add constructor parameters for its dependencies")
        appendLine("3. Wire it in SystemContext, and its fakes in SystemTestContext")
        appendLine("4. Remove the annotation and framework registration")
        appendLine("5. ${u("find-usages", p("type", "Inject"))}; the count should decrease")
    }

    /** Every TaskDef referenced in a `--task` lookup must exist; used by tests. */
    internal fun taskByGoal(goal: String): TaskDef? = TaskRegistry.ALL_TASKS.firstOrNull { it.goal == goal }
}
