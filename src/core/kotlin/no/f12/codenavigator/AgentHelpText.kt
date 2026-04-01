package no.f12.codenavigator

import no.f12.codenavigator.navigation.annotation.FrameworkPresets

object AgentHelpText {
    private val VALID_SECTIONS = setOf("install", "setup", "workflow", "interpretation", "schemas", "extraction")

    fun generate(tool: BuildTool = BuildTool.GRADLE, section: String? = null): String = when (section) {
        null -> generateCompact(tool)
        "install" -> generateInstall(tool)
        "setup" -> generateSetup(tool)
        "workflow" -> generateWorkflow(tool)
        "interpretation" -> generateInterpretation(tool)
        "schemas" -> generateSchemas(tool)
        "extraction" -> generateExtraction(tool)
        else -> "Unknown section: '$section'. Valid sections: ${VALID_SECTIONS.sorted().joinToString(", ")}"
    }

    private fun generateInstall(tool: BuildTool): String = buildString {
        appendLine("## code-navigator")
        appendLine()
        appendLine("code-navigator provides reliable code navigation and code smell analysis for JVM projects.")
        appendLine("It gives structural and behavioral insight into a codebase without reading source files.")
        appendLine("Navigation queries (callers, dependencies, implementors) return accurate, complete results")
        appendLine("in a single call — no iterative searching needed.")
        appendLine()
        appendLine("Run ${tool.usage("agent-help")} for task selection guidance, parameters, and workflows.")
    }

    private fun generateSetup(tool: BuildTool): String = buildString {
        appendLine("=== code-navigator: Setup ===")
        appendLine()
        appendLine("### Permissions")
        appendLine()
        appendLine("If a cnav command triggers a Bash approval prompt, create a permission rule")
        appendLine("so future cnav commands run without prompting:")
        appendLine("1. Copy the command string shown in the approval prompt")
        appendLine("2. Keep everything up to and including `cnav`, replace the rest with `*`")
        appendLine("3. Add as a Bash allow rule in `.claude/settings.local.json`")
        appendLine()
        when (tool) {
            BuildTool.GRADLE -> {
                appendLine("Example — if the prompt shows:")
                appendLine("  `./gradlew cnavListClasses -Pllm=true`")
                appendLine("Add: `\"Bash(./gradlew cnav*)\"`")
                appendLine()
                appendLine("If your command has a preamble (e.g. `eval \"\$(mise activate bash)\" && ./gradlew cnavListClasses ...`),")
                appendLine("include the preamble in the rule: `\"Bash(eval \\\"\\$(mise activate bash)\\\" && ./gradlew cnav*)\"`")
            }
            BuildTool.MAVEN -> {
                appendLine("Example — if the prompt shows:")
                appendLine("  `mvn cnav:find-usages -Dtype=Foo -Dllm=true`")
                appendLine("Add: `\"Bash(mvn cnav:*)\"`")
            }
        }
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
        appendLine("code-navigator is a $pluginType plugin for reliable code navigation and code smell analysis.")
        appendLine("It gives you structural and behavioral insight into a codebase without reading source files.")
        appendLine("Navigation queries return accurate, complete results in a single tool call —")
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
        appendLine("  coupling.")
        appendLine("- Finding interface implementations — ${t("find-interfaces")} resolves polymorphism.")
        appendLine("- Migration planning — ${t("find-usages")} finds all call sites, field accesses, and type")
        appendLine("  references to external APIs.")
        appendLine("- Inspecting class structure — ${t("class-detail")} shows fields, methods, supertypes, annotations")
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
        appendLine("    → ${u("find-usages", p("owner-class", "com.example.Config"), p("field", "timeout"))}")
        appendLine()
        appendLine("  \"Who calls method X?\"")
        appendLine("    → ${u("find-callers", p("pattern", "X"))}")
        appendLine()
        appendLine("  \"What does class X look like?\"")
        appendLine("    → ${u("class-detail", p("pattern", "X"))}")
        appendLine()
        appendLine("  \"What calls does method X make?\"")
        appendLine("    → ${u("find-callees", p("pattern", "X"))}")
        appendLine()
        appendLine("  \"Who implements interface X?\"")
        appendLine("    → ${u("find-interfaces", p("pattern", "X"))}")
        appendLine()
        appendLine("  \"What does class X extend/implement?\"")
        appendLine("    → ${u("type-hierarchy", p("pattern", "X"))}")
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
        appendLine("  \"Where is string X hardcoded?\"")
        appendLine("    → ${u("find-string-constant", p("pattern", "X"))}")
        appendLine()
        appendLine("  \"Which classes use annotation X?\"")
        appendLine("    → ${u("annotations", p("pattern", "X"))}")
        appendLine()
        appendLine("  \"Which methods have annotation X?\"")
        appendLine("    → ${u("annotations", p("pattern", "X"), p("methods", "true"))}")
        appendLine()
        appendLine("  \"What's the blast radius of my changes?\"")
        appendLine("    → ${u("changed-since", p("ref", "main"))}")
        appendLine()
        appendLine("  \"Give me everything about class X\" / \"Full context for X\"")
        appendLine("    → ${u("context", p("pattern", "X"))}")
        appendLine("    # Returns class detail + callers + callees + interfaces in one call")
        appendLine()
        appendLine("  \"How far apart are coupled packages?\"")
        appendLine("    → ${u("distance")}")
        appendLine("    # Shows structural distance between packages that depend on each other")
        appendLine()
        appendLine("--- Task Reference ---")
        appendLine()
        appendTaskReference(tool)
        appendLine()
        appendLine("--- Global Parameters ---")
        appendLine()
        appendGlobalParameters(tool)
        appendLine()
        appendLine("--- Pattern Matching ---")
        appendLine()
        appendLine("All patterns are Java regex, case-insensitive, and substring-matched.")
        appendLine("Simple class names work: 'Owner' matches 'org.example.owner.Owner'.")
        appendLine("FQN patterns work too: 'owner\\.Owner' matches the full qualified name.")
        appendLine()
        appendLine("CamelCase shorthand: at camelCase boundaries, .* is inserted automatically.")
        appendLine("  OwnCont → Own.*Cont  (matches OwnerController, etc.)")
        appendLine()
        appendLine("Enhanced params (auto camelCase expansion): pattern, owner-class, type")
        appendLine("Plain regex params (no expansion): method, field, filter, exclude, find-string-constant's pattern")
        appendLine()
        appendLine("--- Tips for Optimal Results ---")
        appendLine()
        appendLine("- Always use ${p("llm", "true")} for compact output. Omit only for human-readable display.")
        appendLine("- Use ${p("project-only", "true")} to cut noise from JDK/stdlib classes.")
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
        appendLine("   ${u("find-callers", p("pattern", "resetPassword"), p("maxdepth", "3"), p("project-only", "true"))}  # who calls this?")
        appendLine("   ${u("find-callees", p("pattern", "handleRequest"), p("maxdepth", "3"), p("project-only", "false"))}  # what does this call?")
        appendLine("   # ${p("project-only", "true")} hides JDK/stdlib noise")
        appendLine("   # ${p("maxdepth", "5")} for deeper traversal")
        appendLine()
        appendLine("5. MAP: Understand abstractions")
        appendLine("   ${u("find-interfaces", p("pattern", "Repository"))}")
        appendLine("   # Test source sets are included by default — test fakes/stubs are visible")
        appendLine("   # Use ${p("test-only", "true")} to see only test fakes and stubs")
        appendLine("   ${u("type-hierarchy", p("pattern", "Service"))}")
        appendLine("   # Shows superclass chain upward + implementors downward")
        appendLine()
        appendLine("6. MIGRATE: Find external API usage for migration")
        appendLine("   ${u("find-usages", p("owner-class", "kotlinx.datetime.LocalDate"), p("method", "getMonthNumber"))}")
        appendLine("   ${u("find-usages", p("owner-class", "com.example.Config"), p("field", "timeout"))}  # field reads/writes + property accessors")
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
        appendLine("   ${u("rank", p("project-only", "false"))}        # include external types")
        appendLine()
        appendLine("9. DEAD CODE: Find unreferenced classes and methods")
        appendLine("   Kotlin noise (data class boilerplate, companion objects, coroutine inner classes) is auto-filtered.")
        appendLine("   ${u("dead")}                         # all potential dead code")
        appendLine("   ${u("dead", p("filter", "service"))}             # only show matches")
        appendLine("   ${u("dead", p("exclude", "\"Main|Test\""))}    # exclude entry points")
        appendLine("   ${u("dead", p("exclude", "\"com\\\\.example\\\\.grpc\""))}  # exclude generated packages")
        appendLine("   ${u("dead", p("classes-only", "true"))}         # only unreferenced classes")
        appendLine("   ${u("dead", p("exclude-annotated", "\"RestController,Scheduled\""))}  # skip framework-annotated")
        appendLine("   ${u("dead", p("prod-only", "true"))}              # only items with zero references (production + test)")
        appendLine("   ${u("dead", p("test-only", "true"))}              # only items referenced exclusively from tests")
        appendLine("   ${u("dead", p("treat-as-dead", "spring"))}      # treat Spring-annotated code as potentially dead")
        appendLine("   ${u("dead", p("treat-as-dead", "ALL"))}         # treat all framework-annotated code as potentially dead")
        appendLine()
        appendLine("10. COMPLEXITY: Measure class coupling (fan-in/fan-out)")
        appendLine("   ${u("complexity")}                        # all classes sorted by fan-out")
        appendLine("   ${u("complexity", p("pattern", "Service"))}       # fan-in/out for matching classes")
        appendLine("   ${u("complexity", p("pattern", "\".*Controller\""), p("detail", "true"))} # with call details")
        appendLine()
        appendLine("11. METRICS: Quick project health snapshot")
        appendLine("   ${u("metrics")}                       # summary: classes, packages, fan-in/out, cycles, dead code, hotspots")
        appendLine("   ${u("metrics", p("top", "10"))}                # top 10 hotspots")
        appendLine("   ${u("metrics", p("package-filter", "com.example"))} # scope structural analysis to package")
        appendLine()
        appendLine("12. DISTANCE: Structural distance between coupled packages")
        appendLine("   ${u("distance")}                      # all dependency edges with tree distance")
        appendLine("   ${u("distance", p("top", "20"))}               # top 20 most distant coupled packages")
        appendLine("   ${u("distance", p("package-filter", "com.example"))} # scope to a package prefix")
        appendLine()
        appendLine("13. STRING CONSTANTS: Find hardcoded strings in compiled code")
        appendLine("   ${u("find-string-constant", p("pattern", "\"http://\""))}      # find URLs")
        appendLine("   ${u("find-string-constant", p("pattern", "\"SELECT.*FROM\""))} # find SQL")
        appendLine("   ${u("find-string-constant", p("pattern", "\"password|secret\""))} # find sensitive strings")
        appendLine()
        appendLine("14. ANNOTATIONS: Find classes and methods by annotation")
        appendLine("   ${u("annotations", p("pattern", "RestController"))}          # find all @RestController classes")
        appendLine("   ${u("annotations", p("pattern", "Mapping"), p("methods", "true"))}  # find @*Mapping on classes and methods")
        appendLine("   ${u("annotations", p("pattern", "Scheduled"), p("methods", "true"))} # find all @Scheduled methods")
        appendLine()
        appendLine("15. IMPACT: See what's affected by recent changes")
        appendLine("   ${u("changed-since", p("ref", "main"))}           # blast radius vs main branch")
        appendLine("   ${u("changed-since", p("ref", "v1.2.0"))}          # blast radius since a tag")
        appendLine("   ${u("changed-since", p("ref", "HEAD~5"))}          # blast radius of last 5 commits")
        appendLine("   # Shows changed classes, their callers, and unresolved files (non-class changes)")
    }

    private fun generateInterpretation(tool: BuildTool): String = buildString {
        val t = { goal: String -> tool.taskName(goal) }
        val p = { name: String, value: String -> tool.param(name, value) }

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
        appendLine("- Each result includes a confidence level: HIGH, MEDIUM, or LOW.")
        appendLine("  HIGH = unreferenced in both production and test code.")
        appendLine("  MEDIUM = unreferenced in production but referenced in test code.")
        appendLine("  LOW = has framework annotations (likely invoked via reflection).")
        appendLine("- Each result includes a reason: NO_REFERENCES or TEST_ONLY.")
        appendLine("  NO_REFERENCES = no incoming references at all.")
        appendLine("  TEST_ONLY = referenced only in test code, not production.")
        appendLine("- Use ${p("prod-only", "true")} to hide TEST_ONLY items and focus on truly unreferenced code.")
        appendLine("- Use ${p("test-only", "true")} to show only TEST_ONLY items (code referenced exclusively from tests).")
        appendLine("- All framework presets are active by default — no configuration needed for Spring, Quarkus, or other frameworks.")
        appendLine("  Use ${p("treat-as-dead", "<preset>")} to treat a specific framework's annotated code as potentially dead.")
        appendLine("  Available presets: ${FrameworkPresets.availablePresets().sorted().joinToString(", ")}.")
        appendLine("  Use ${p("treat-as-dead", "ALL")} to remove all framework protections.")
        appendLine("  Composite presets: 'quarkus' includes jaxrs + cdi + microprofile + jpa + jakarta + validation + jackson.")
        appendLine("  Entry-point annotations (e.g. @GetMapping, @Scheduled) → excluded from results.")
        appendLine("  Modifier annotations (e.g. @Transactional, @Cacheable) → reported with LOW confidence.")
        appendLine("  Supertype entry points (e.g. JpaRepository, PanacheRepository) → excluded from results.")
        appendLine("  Receiver type entry points (e.g. Ktor Route/Application extension functions) → excluded from results.")
        appendLine("- Use -Pexclude-annotated to suppress additional entry points not covered by presets.")
        appendLine("- Use ${p("exclude", "<regex>")} to exclude entire packages (e.g. generated protobuf code).")
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
        appendLine("    \"annotations\": [{\"name\": \"...\", \"parameters\": {}}],")
        appendLine("    \"interfaces\": [\"...\"], \"fields\": [{\"name\": \"...\", \"type\": \"...\", \"annotations\": [...]}],")
        appendLine("    \"methods\": [{\"name\": \"...\", \"parameters\": [\"...\"], \"returnType\": \"...\", \"annotations\": [...]}]}]")
        appendLine()
        appendLine("${t("find-callers")} / ${t("find-callees")}:")
        appendLine("  [{\"method\": \"com.example.Service.doWork\", \"sourceFile\": \"Service.kt\",")
        appendLine("    \"children\": [{\"method\": \"...\", \"sourceFile\": \"...\", \"children\": [...]}]}]")
        appendLine()
        appendLine("${t("find-interfaces")}:")
        appendLine("  [{\"interface\": \"com.example.Repository\",")
        appendLine("    \"implementors\": [{\"className\": \"com.example.SqlRepo\", \"sourceFile\": \"SqlRepo.kt\"}]}]")
        appendLine()
        appendLine("${t("type-hierarchy")}:")
        appendLine("  [{\"className\": \"com.example.SqlRepo\", \"sourceFile\": \"SqlRepo.kt\",")
        appendLine("    \"supertypes\": [{\"className\": \"com.example.Repository\", \"kind\": \"INTERFACE\", \"supertypes\": []}],")
        appendLine("    \"implementors\": []}]")
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
        appendLine("  [{\"className\": \"com.example.Orphan\", \"kind\": \"class\", \"sourceFile\": \"Orphan.kt\", \"confidence\": \"high\", \"reason\": \"NO_REFERENCES\"},")
        appendLine("   {\"className\": \"com.example.Service\", \"memberName\": \"unused\", \"kind\": \"method\", \"sourceFile\": \"Service.kt\", \"confidence\": \"medium\", \"reason\": \"TEST_ONLY\"}]")
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
        appendLine()
        appendLine("${t("find-string-constant")}:")
        appendLine("  [{\"className\": \"com.example.Service\", \"methodName\": \"connect\", \"value\": \"http://localhost:8080\", \"sourceFile\": \"Service.kt\"}]")
        appendLine()
        appendLine("${t("annotations")}:")
        appendLine("  [{\"className\": \"com.example.MyController\", \"sourceFile\": \"MyController.kt\",")
        appendLine("    \"classAnnotations\": [\"RestController\"],")
        appendLine("    \"methods\": [{\"method\": \"getUsers\", \"annotations\": [\"GetMapping\"]}]}]")
        appendLine()
        appendLine("${t("changed-since")}:")
        appendLine("  {\"changedClasses\": [{\"className\": \"com.example.Service\", \"sourceFile\": \"Service.kt\",")
        appendLine("    \"callers\": [{\"className\": \"com.example.Controller\", \"method\": \"handle\"}]}],")
        appendLine("   \"unresolvedFiles\": [\"build.gradle.kts\"]}")
        appendLine()
        appendLine("${t("context")}:")
        appendLine("  {\"classDetail\": {\"className\": \"...\", \"sourceFile\": \"...\", ...},")
        appendLine("   \"callers\": [{\"method\": \"...\", \"children\": [...]}],")
        appendLine("   \"callees\": [{\"method\": \"...\", \"children\": [...]}],")
        appendLine("   \"implementors\": [{\"className\": \"...\", \"sourceFile\": \"...\"}],")
        appendLine("   \"implementedInterfaces\": [\"...\"]}")
        appendLine()
        appendLine("${t("distance")}:")
        appendLine("  [{\"source\": \"com.example.api\", \"target\": \"com.example.persistence\", \"distance\": 2, \"deps\": 5}]")
        appendLine()
        appendLine("Empty Results:")
        appendLine("  When a query returns no results, JSON/LLM output uses a wrapper with hints:")
        appendLine("  {\"results\":[],\"hints\":[\"Try -Pmethods=true to include method-level annotations\"]}")
        appendLine("  The hints array contains actionable suggestions. It may be empty: {\"results\":[],\"hints\":[]}")
        appendLine("  TEXT output appends hints as plain text lines after the message.")
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
        appendLine("  $helperName ${t("find-callers")} ${p("pattern", "save")} ${p("maxdepth", "3")} ${p("format", "json")} | jq '.[].children[].method'")
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
        appendLine("  ${p("section", "setup")}            — permissions and environment setup")
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
