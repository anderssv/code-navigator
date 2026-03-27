package no.f12.codenavigator

import no.f12.codenavigator.navigation.PatternEnhancer

data class ParamDef(
    val name: String,
    val valuePlaceholder: String,
    val description: String,
    val flag: Boolean,
    val defaultValue: String?,
    val enhancePattern: Boolean,
) {
    fun render(tool: BuildTool): String = when (flag) {
        true -> tool.paramFlag(name)
        false -> tool.param(name, valuePlaceholder)
    }
}

data class TaskDef(
    val goal: String,
    val description: String,
    val params: List<ParamDef>,
    val requiresCompilation: Boolean,
) {
    fun taskName(tool: BuildTool): String = tool.taskName(goal)

    fun paramByName(name: String): ParamDef =
        params.first { it.name == name }

    fun enhanceProperties(properties: Map<String, String?>): Map<String, String?> {
        val enhancedNames = params.filter { it.enhancePattern }.map { it.name }.toSet()
        return properties.mapValues { (key, value) ->
            if (value != null && key in enhancedNames) PatternEnhancer.enhance(value) else value
        }
    }
}

object TaskRegistry {

    // --- Shared parameter definitions ---

    val FORMAT = ParamDef("format", "json", "Output as machine-readable JSON", flag = false, defaultValue = null, enhancePattern = false)
    val LLM = ParamDef("llm", "true", "Output in compact, token-efficient LLM format", flag = false, defaultValue = null, enhancePattern = false)
    val PATTERN = ParamDef("pattern", "<regex>", "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)", flag = false, defaultValue = null, enhancePattern = true)
    val METHOD = ParamDef("method", "<regex>", "Method name regex", flag = false, defaultValue = null, enhancePattern = false)
    val MAXDEPTH = ParamDef("maxdepth", "<N>", "Max call tree depth", flag = false, defaultValue = "3", enhancePattern = false)
    val PROJECTONLY = ParamDef("projectonly", "true", "Hide JDK/stdlib/library classes", flag = false, defaultValue = null, enhancePattern = false)
    val FILTER_SYNTHETIC = ParamDef("filter-synthetic", "false", "Set false to include synthetic methods (equals, hashCode, copy, componentN, etc.)", flag = false, defaultValue = "true", enhancePattern = false)
    val TOP = ParamDef("top", "<N>", "Max results", flag = false, defaultValue = "50", enhancePattern = false)
    val AFTER = ParamDef("after", "YYYY-MM-DD", "Only consider commits after this date", flag = false, defaultValue = "1 year ago", enhancePattern = false)
    val NO_FOLLOW = ParamDef("no-follow", "", "Disable git rename tracking", flag = true, defaultValue = null, enhancePattern = false)
    val MIN_REVS = ParamDef("min-revs", "<N>", "Min revisions to include", flag = false, defaultValue = "1", enhancePattern = false)

    // --- Task-specific parameter definitions ---

    private val INCLUDETEST = ParamDef("includetest", "true", "Include test source set", flag = false, defaultValue = null, enhancePattern = false)
    private val PACKAGE = ParamDef("package", "<regex>", "Filter packages by regex", flag = false, defaultValue = null, enhancePattern = false)
    private val REVERSE = ParamDef("reverse", "true", "Show reverse dependencies", flag = false, defaultValue = null, enhancePattern = false)
    private val ROOT_PACKAGE = ParamDef("root-package", "<pkg>", "Only include packages under this prefix", flag = false, defaultValue = "all", enhancePattern = false)
    private val DSM_DEPTH = ParamDef("dsm-depth", "<N>", "Package grouping depth", flag = false, defaultValue = "2", enhancePattern = false)
    private val DSM_HTML = ParamDef("dsm-html", "<path>", "Write interactive HTML matrix to file", flag = false, defaultValue = null, enhancePattern = false)
    private val CYCLES = ParamDef("cycles", "true", "Show only cyclic dependencies with class-level edges", flag = false, defaultValue = null, enhancePattern = false)
    private val CYCLE = ParamDef("cycle", "<pkgA>,<pkgB>", "Show only the cycle between two specific packages", flag = false, defaultValue = null, enhancePattern = false)
    private val OWNER_CLASS = ParamDef("ownerClass", "<class>", "Class name or pattern — matches method call and field owners (camelCase-aware: MyService matches com.example.MyService)", flag = false, defaultValue = null, enhancePattern = true)
    private val FIELD = ParamDef("field", "<name>", "Field/property name — also finds getter/setter calls", flag = false, defaultValue = null, enhancePattern = false)
    private val TYPE = ParamDef("type", "<class>", "Find ALL references to a class: calls, fields, casts, signatures (camelCase-aware)", flag = false, defaultValue = null, enhancePattern = true)
    private val OUTSIDE_PACKAGE = ParamDef("outside-package", "<pkg>", "Exclude callers inside this package", flag = false, defaultValue = null, enhancePattern = false)
    private val FILTER = ParamDef("filter", "<regex>", "Only show results matching this regex", flag = false, defaultValue = null, enhancePattern = false)
    private val EXCLUDE = ParamDef("exclude", "<regex>", "Exclude results matching this regex", flag = false, defaultValue = null, enhancePattern = false)
    private val CLASSES_ONLY = ParamDef("classes-only", "true", "Show only unreferenced classes, skip dead methods", flag = false, defaultValue = null, enhancePattern = false)
    private val CLASS = ParamDef("classname", "<pattern>", "Class name regex to analyze", flag = false, defaultValue = ".*", enhancePattern = false)
    private val DETAIL = ParamDef("detail", "true", "Show individual call details", flag = false, defaultValue = null, enhancePattern = false)
    private val MIN_SHARED_REVS = ParamDef("min-shared-revs", "<N>", "Min shared commits", flag = false, defaultValue = "5", enhancePattern = false)
    private val MIN_COUPLING = ParamDef("min-coupling", "<N>", "Min coupling degree %", flag = false, defaultValue = "30", enhancePattern = false)
    private val MAX_CHANGESET_SIZE = ParamDef("max-changeset-size", "<N>", "Skip commits touching more files", flag = false, defaultValue = "30", enhancePattern = false)
    private val SECTION = ParamDef("section", "<name>", "Help section: install, workflow, interpretation, schemas, extraction", flag = false, defaultValue = null, enhancePattern = false)

    private val FORMAT_PARAMS = listOf(FORMAT, LLM)

    // --- Task definitions ---

    val LIST_CLASSES = TaskDef(
        goal = "list-classes",
        description = "List all classes in the project",
        params = FORMAT_PARAMS,
        requiresCompilation = true,
    )

    val FIND_CLASS = TaskDef(
        goal = "find-class",
        description = "Find classes matching a regex pattern",
        params = FORMAT_PARAMS + PATTERN,
        requiresCompilation = true,
    )

    val FIND_SYMBOL = TaskDef(
        goal = "find-symbol",
        description = "Find methods and fields matching a regex pattern",
        params = FORMAT_PARAMS + PATTERN,
        requiresCompilation = true,
    )

    val CLASS_DETAIL = TaskDef(
        goal = "class-detail",
        description = "Show detailed class information (methods, fields, interfaces)",
        params = FORMAT_PARAMS + PATTERN,
        requiresCompilation = true,
    )

    val FIND_CALLERS = TaskDef(
        goal = "find-callers",
        description = "Find callers of a method (call tree)",
        params = FORMAT_PARAMS + listOf(METHOD, MAXDEPTH, PROJECTONLY, FILTER_SYNTHETIC),
        requiresCompilation = true,
    )

    val FIND_CALLEES = TaskDef(
        goal = "find-callees",
        description = "Find methods called by a method (call tree)",
        params = FORMAT_PARAMS + listOf(METHOD, MAXDEPTH, PROJECTONLY, FILTER_SYNTHETIC),
        requiresCompilation = true,
    )

    val FIND_INTERFACES = TaskDef(
        goal = "find-interfaces",
        description = "Find implementations of an interface",
        params = FORMAT_PARAMS + listOf(PATTERN, INCLUDETEST),
        requiresCompilation = true,
    )

    val PACKAGE_DEPS = TaskDef(
        goal = "package-deps",
        description = "Show package-level dependencies",
        params = FORMAT_PARAMS + listOf(PACKAGE, PROJECTONLY, REVERSE),
        requiresCompilation = true,
    )

    val DSM = TaskDef(
        goal = "dsm",
        description = "Generate Dependency Structure Matrix",
        params = FORMAT_PARAMS + listOf(ROOT_PACKAGE, DSM_DEPTH, DSM_HTML, CYCLES, CYCLE),
        requiresCompilation = true,
    )

    val CYCLE_DETECTION = TaskDef(
        goal = "cycles",
        description = "Detect dependency cycles using Tarjan's SCC algorithm",
        params = FORMAT_PARAMS + listOf(ROOT_PACKAGE, DSM_DEPTH),
        requiresCompilation = true,
    )

    val FIND_USAGES = TaskDef(
        goal = "find-usages",
        description = "Find project references to types, methods, and fields/properties",
        params = FORMAT_PARAMS + listOf(OWNER_CLASS, METHOD, FIELD, TYPE, OUTSIDE_PACKAGE),
        requiresCompilation = true,
    )

    val RANK = TaskDef(
        goal = "rank",
        description = "Rank types by importance (PageRank on call graph)",
        params = FORMAT_PARAMS + listOf(TOP, PROJECTONLY),
        requiresCompilation = true,
    )

    val DEAD = TaskDef(
        goal = "dead",
        description = "Detect dead code (unreferenced classes and methods)",
        params = FORMAT_PARAMS + listOf(FILTER, EXCLUDE, CLASSES_ONLY),
        requiresCompilation = true,
    )

    val HOTSPOTS = TaskDef(
        goal = "hotspots",
        description = "Rank files by change frequency",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_REVS, TOP, NO_FOLLOW),
        requiresCompilation = false,
    )

    val CHURN = TaskDef(
        goal = "churn",
        description = "Show code churn (lines added/deleted per file)",
        params = FORMAT_PARAMS + listOf(AFTER, TOP, NO_FOLLOW),
        requiresCompilation = false,
    )

    val CODE_AGE = TaskDef(
        goal = "code-age",
        description = "Show time since last modification per file",
        params = FORMAT_PARAMS + listOf(AFTER, TOP, NO_FOLLOW),
        requiresCompilation = false,
    )

    val AUTHORS = TaskDef(
        goal = "authors",
        description = "Show number of distinct contributors per file",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_REVS, TOP, NO_FOLLOW),
        requiresCompilation = false,
    )

    val COUPLING = TaskDef(
        goal = "coupling",
        description = "Find files that change together (temporal coupling)",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_SHARED_REVS, MIN_COUPLING, MAX_CHANGESET_SIZE, TOP, NO_FOLLOW),
        requiresCompilation = false,
    )

    val COMPLEXITY = TaskDef(
        goal = "complexity",
        description = "Show fan-in/fan-out complexity per class",
        params = FORMAT_PARAMS + listOf(CLASS, PROJECTONLY, DETAIL, TOP),
        requiresCompilation = true,
    )

    val METRICS = TaskDef(
        goal = "metrics",
        description = "Quick project health snapshot: classes, packages, fan-in/out, cycles, dead code, hotspots",
        params = FORMAT_PARAMS + listOf(AFTER, TOP, NO_FOLLOW, ROOT_PACKAGE),
        requiresCompilation = true,
    )

    val HELP = TaskDef(
        goal = "help",
        description = "Show help text with available tasks",
        params = emptyList(),
        requiresCompilation = false,
    )

    val AGENT_HELP = TaskDef(
        goal = "agent-help",
        description = "Show workflow guidance for AI coding agents",
        params = listOf(SECTION),
        requiresCompilation = false,
    )

    val CONFIG_HELP = TaskDef(
        goal = "config-help",
        description = "Show configuration reference for all parameters",
        params = emptyList(),
        requiresCompilation = false,
    )

    val ALL_TASKS: List<TaskDef> = listOf(
        LIST_CLASSES,
        FIND_CLASS,
        FIND_SYMBOL,
        CLASS_DETAIL,
        FIND_CALLERS,
        FIND_CALLEES,
        FIND_INTERFACES,
        PACKAGE_DEPS,
        DSM,
        CYCLE_DETECTION,
        FIND_USAGES,
        RANK,
        DEAD,
        COMPLEXITY,
        METRICS,
        HOTSPOTS,
        CHURN,
        CODE_AGE,
        AUTHORS,
        COUPLING,
        HELP,
        AGENT_HELP,
        CONFIG_HELP,
    )
}
