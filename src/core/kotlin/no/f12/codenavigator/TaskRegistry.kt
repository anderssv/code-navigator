package no.f12.codenavigator

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.PatternEnhancer
import no.f12.codenavigator.navigation.annotation.FrameworkPresets
import java.time.LocalDate

enum class TaskCategory {
    NAVIGATION,
    GIT_HISTORY,
    HYBRID,
    HELP,
}

sealed class ParamType<T>(val parse: (value: String?, defaultValue: String?) -> T) {
    data object STRING : ParamType<String?>(
        parse = { value, _ -> value },
    )

    data object LIST_STRING : ParamType<List<String>>(
        parse = { value, _ ->
            value
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        },
    )

    data object BOOLEAN : ParamType<Boolean>(
        parse = { value, defaultValue ->
            value?.toBoolean() ?: (defaultValue?.toBoolean() ?: false)
        },
    )

    data object INT : ParamType<Int>(
        parse = { value, defaultValue ->
            value?.toIntOrNull() ?: defaultValue?.toIntOrNull() ?: 0
        },
    )

    data object FLAG : ParamType<Boolean>(
        parse = { value, _ ->
            value != null
        },
    )

    data object DATE : ParamType<LocalDate>(
        parse = { value, _ ->
            value?.let { LocalDate.parse(it) } ?: LocalDate.now().minusYears(1)
        },
    )
}

data class ParamDef<T>(
    val name: String,
    val valuePlaceholder: String,
    val description: String,
    val flag: Boolean,
    val defaultValue: String?,
    val enhancePattern: Boolean,
    val type: ParamType<T>,
) {
    fun render(tool: BuildTool): String = when (flag) {
        true -> tool.paramFlag(name)
        false -> tool.param(name, valuePlaceholder)
    }

    fun parse(value: String?): T = type.parse(value, defaultValue)

    fun parseFrom(properties: Map<String, String?>): T =
        if (type is ParamType.FLAG) {
            @Suppress("UNCHECKED_CAST")
            (properties.containsKey(name) as T)
        } else {
            parse(properties[name])
        }

    companion object {
        fun parseFormat(properties: Map<String, String?>): OutputFormat =
            OutputFormat.from(
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
            )
    }
}

data class TaskDef(
    val goal: String,
    val description: String,
    val params: List<ParamDef<*>>,
    val requiresCompilation: Boolean,
    val category: TaskCategory,
    val legacyGradleTaskName: String? = null,
) {
    val gradleTaskName: String = goalToGradleTaskName(goal)

    fun taskName(tool: BuildTool): String = tool.taskName(goal)

    fun paramByName(name: String): ParamDef<*> =
        params.first { it.name == name }

    fun enhanceProperties(properties: Map<String, String?>): Map<String, String?> {
        val enhancedNames = params.filter { it.enhancePattern }.map { it.name }.toSet()
        return properties.mapValues { (key, value) ->
            if (value != null && key in enhancedNames) PatternEnhancer.enhance(value) else value
        }
    }

    companion object {
        fun goalToGradleTaskName(goal: String): String =
            "cnav" + goal.split("-").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}

object TaskRegistry {

    // --- Shared parameter definitions ---

    val FORMAT = ParamDef("format", "json", "Output as machine-readable JSON", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val LLM = ParamDef("llm", "true", "Output in compact, token-efficient LLM format", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val PATTERN = ParamDef("pattern", "<regex>", "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
    val CALL_PATTERN = ParamDef("pattern", "<regex>", "Class.method name regex (camelCase-aware: MyService.doWork matches com.example.MyService.doWork)", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
    val METHOD = ParamDef("method", "<regex>", "Method name regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val MAXDEPTH = ParamDef("maxdepth", "<N>", "Max call tree depth", flag = false, defaultValue = "3", enhancePattern = false, type = ParamType.INT)
    val PROJECTONLY = ParamDef("project-only", "true", "Hide JDK/stdlib/library classes", flag = false, defaultValue = "false", enhancePattern = false, type = ParamType.BOOLEAN)
    val PROJECTONLY_ON = ParamDef("project-only", "true", "Hide JDK/stdlib/library classes (default: on)", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)
    val FILTER_SYNTHETIC = ParamDef("filter-synthetic", "false", "Set false to include synthetic methods (equals, hashCode, copy, componentN, etc.)", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)
    val TOP = ParamDef("top", "<N>", "Max results", flag = false, defaultValue = "50", enhancePattern = false, type = ParamType.INT)
    val AFTER = ParamDef("after", "YYYY-MM-DD", "Only consider commits after this date", flag = false, defaultValue = "1 year ago", enhancePattern = false, type = ParamType.DATE)
    val NO_FOLLOW = ParamDef("no-follow", "", "Disable git rename tracking", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)
    val MIN_REVS = ParamDef("min-revs", "<N>", "Min revisions to include", flag = false, defaultValue = "1", enhancePattern = false, type = ParamType.INT)

    // --- Task-specific parameter definitions ---

    val INCLUDETEST = ParamDef("include-test", "true", "Include test source set", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val PACKAGE = ParamDef("package", "<regex>", "Filter packages by regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val REVERSE = ParamDef("reverse", "true", "Show reverse dependencies", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val ROOT_PACKAGE = ParamDef("root-package", "<pkg>", "Deprecated: use package-filter instead. Only include packages under this prefix", flag = false, defaultValue = "all", enhancePattern = false, type = ParamType.STRING)
    val PACKAGE_FILTER = ParamDef("package-filter", "<pkg>", "Only include packages under this prefix", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val INCLUDE_EXTERNAL = ParamDef("include-external", "true", "Include dependencies on classes outside the project", flag = false, defaultValue = "false", enhancePattern = false, type = ParamType.BOOLEAN)
    val DSM_DEPTH = ParamDef("dsm-depth", "<N>", "Package grouping depth", flag = false, defaultValue = "2", enhancePattern = false, type = ParamType.INT)
    val DSM_HTML = ParamDef("dsm-html", "<path>", "Write interactive HTML matrix to file", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val CYCLES = ParamDef("cycles", "true", "Show only cyclic dependencies with class-level edges", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val CYCLE = ParamDef("cycle", "<pkgA>,<pkgB>", "Show only the cycle between two specific packages", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val OWNER_CLASS = ParamDef("owner-class", "<class>", "Class name or pattern — matches method call and field owners (camelCase-aware: MyService matches com.example.MyService)", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
    val FIELD = ParamDef("field", "<name>", "Field/property name — also finds getter/setter calls", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val TYPE = ParamDef("type", "<class>", "Find ALL references to a class: calls, fields, casts, signatures (camelCase-aware)", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
    val OUTSIDE_PACKAGE = ParamDef("outside-package", "<pkg>", "Exclude callers inside this package", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val FILTER = ParamDef("filter", "<regex>", "Only show results matching this regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val EXCLUDE = ParamDef("exclude", "<regex>", "Exclude results matching this regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val CLASSES_ONLY = ParamDef("classes-only", "true", "Show only unreferenced classes, skip dead methods", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val EXCLUDE_ANNOTATED = ParamDef("exclude-annotated", "<ann1>,<ann2>", "Exclude classes/methods bearing these annotations (simple names, comma-separated)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.LIST_STRING)
    val PROD_ONLY = ParamDef("prod-only", "true", "Show only items from production source set", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val TEST_ONLY = ParamDef("test-only", "true", "Show only items from test source set", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val EXCLUDE_FRAMEWORK = ParamDef("exclude-framework", "<name>", "Disable framework preset (all active by default). Available: ${FrameworkPresets.availablePresets().sorted().joinToString(", ")}. Use ALL to disable all.", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.LIST_STRING)
    val DETAIL = ParamDef("detail", "true", "Show individual call details", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val COLLAPSE_LAMBDAS = ParamDef("collapse-lambdas", "false", "Set false to show lambda classes separately", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)
    val MIN_SHARED_REVS = ParamDef("min-shared-revs", "<N>", "Min shared commits", flag = false, defaultValue = "5", enhancePattern = false, type = ParamType.INT)
    val MIN_COUPLING = ParamDef("min-coupling", "<N>", "Min coupling degree %", flag = false, defaultValue = "30", enhancePattern = false, type = ParamType.INT)
    val MAX_CHANGESET_SIZE = ParamDef("max-changeset-size", "<N>", "Skip commits touching more files", flag = false, defaultValue = "30", enhancePattern = false, type = ParamType.INT)
    val METRICS_TOP = ParamDef("top", "<N>", "Max results per section", flag = false, defaultValue = "5", enhancePattern = false, type = ParamType.INT)
    val SECTION = ParamDef("section", "<name>", "Help section: install, workflow, interpretation, schemas, extraction", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val REF = ParamDef("ref", "<git-ref>", "Git ref to compare against (branch, tag, or commit SHA)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val STRING_PATTERN = ParamDef("pattern", "<regex>", "Regex to match against string constant values", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val METHODS = ParamDef("methods", "true", "Also search method-level annotations", flag = false, defaultValue = "false", enhancePattern = false, type = ParamType.BOOLEAN)
    val CONTEXT_MAXDEPTH = ParamDef("maxdepth", "<N>", "Max call tree depth (default: 2)", flag = false, defaultValue = "2", enhancePattern = false, type = ParamType.INT)

    val FORMAT_PARAMS = listOf(FORMAT, LLM)

    // --- Task definitions ---

    val LIST_CLASSES = TaskDef(
        goal = "list-classes",
        description = "List all classes in the project",
        params = FORMAT_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val FIND_CLASS = TaskDef(
        goal = "find-class",
        description = "Find classes matching a regex pattern",
        params = FORMAT_PARAMS + PATTERN,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val FIND_SYMBOL = TaskDef(
        goal = "find-symbol",
        description = "Find methods and fields matching a regex pattern",
        params = FORMAT_PARAMS + PATTERN,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val CLASS_DETAIL = TaskDef(
        goal = "class-detail",
        description = "Show detailed class information (methods, fields, interfaces)",
        params = FORMAT_PARAMS + PATTERN,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavClass",
    )

    val FIND_CALLERS = TaskDef(
        goal = "find-callers",
        description = "Find callers of a method (call tree)",
        params = FORMAT_PARAMS + listOf(CALL_PATTERN, MAXDEPTH, PROJECTONLY, FILTER_SYNTHETIC, PROD_ONLY, TEST_ONLY),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavCallers",
    )

    val FIND_CALLEES = TaskDef(
        goal = "find-callees",
        description = "Find methods called by a method (call tree)",
        params = FORMAT_PARAMS + listOf(CALL_PATTERN, MAXDEPTH, PROJECTONLY, FILTER_SYNTHETIC, PROD_ONLY, TEST_ONLY),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavCallees",
    )

    val FIND_INTERFACES = TaskDef(
        goal = "find-interfaces",
        description = "Find implementations of an interface",
        params = FORMAT_PARAMS + listOf(PATTERN, INCLUDETEST),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavInterfaces",
    )

    val TYPE_HIERARCHY = TaskDef(
        goal = "type-hierarchy",
        description = "Show type hierarchy (supertypes upward, implementors downward)",
        params = FORMAT_PARAMS + listOf(PATTERN, PROJECTONLY),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val PACKAGE_DEPS = TaskDef(
        goal = "package-deps",
        description = "Show package-level dependencies",
        params = FORMAT_PARAMS + listOf(PACKAGE, PROJECTONLY, REVERSE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavDeps",
    )

    val DSM = TaskDef(
        goal = "dsm",
        description = "Generate Dependency Structure Matrix",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, DSM_HTML, CYCLES, CYCLE, ROOT_PACKAGE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val CYCLE_DETECTION = TaskDef(
        goal = "cycles",
        description = "Detect dependency cycles using Tarjan's SCC algorithm",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, ROOT_PACKAGE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val FIND_USAGES = TaskDef(
        goal = "find-usages",
        description = "Find project references to types, methods, and fields/properties",
        params = FORMAT_PARAMS + listOf(OWNER_CLASS, METHOD, FIELD, TYPE, OUTSIDE_PACKAGE, PROD_ONLY, TEST_ONLY),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavUsages",
    )

    val RANK = TaskDef(
        goal = "rank",
        description = "Rank types by importance (PageRank on call graph)",
        params = FORMAT_PARAMS + listOf(TOP, PROJECTONLY_ON, COLLAPSE_LAMBDAS, PROD_ONLY, TEST_ONLY),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val DEAD = TaskDef(
        goal = "dead",
        description = "Detect dead code (unreferenced classes and methods)",
        params = FORMAT_PARAMS + listOf(FILTER, EXCLUDE, CLASSES_ONLY, EXCLUDE_ANNOTATED, PROD_ONLY, EXCLUDE_FRAMEWORK),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val HOTSPOTS = TaskDef(
        goal = "hotspots",
        description = "Rank files by change frequency",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_REVS, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
    )

    val CHURN = TaskDef(
        goal = "churn",
        description = "Show code churn (lines added/deleted per file)",
        params = FORMAT_PARAMS + listOf(AFTER, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
    )

    val CODE_AGE = TaskDef(
        goal = "code-age",
        description = "Show time since last modification per file",
        params = FORMAT_PARAMS + listOf(AFTER, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
        legacyGradleTaskName = "cnavAge",
    )

    val AUTHORS = TaskDef(
        goal = "authors",
        description = "Show number of distinct contributors per file",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_REVS, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
    )

    val COUPLING = TaskDef(
        goal = "coupling",
        description = "Find files that change together (temporal coupling)",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_SHARED_REVS, MIN_COUPLING, MAX_CHANGESET_SIZE, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
    )

    val COMPLEXITY = TaskDef(
        goal = "complexity",
        description = "Show fan-in/fan-out complexity per class",
        params = FORMAT_PARAMS + listOf(PATTERN, PROJECTONLY_ON, DETAIL, COLLAPSE_LAMBDAS, TOP, PROD_ONLY, TEST_ONLY),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val METRICS = TaskDef(
        goal = "metrics",
        description = "Quick project health snapshot: classes, packages, fan-in/out, cycles, dead code, hotspots",
        params = FORMAT_PARAMS + listOf(AFTER, METRICS_TOP, NO_FOLLOW, PACKAGE_FILTER, INCLUDE_EXTERNAL, EXCLUDE_ANNOTATED, EXCLUDE_FRAMEWORK, ROOT_PACKAGE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val HELP = TaskDef(
        goal = "help",
        description = "Show help text with available tasks",
        params = emptyList(),
        requiresCompilation = false,
        category = TaskCategory.HELP,
    )

    val AGENT_HELP = TaskDef(
        goal = "agent-help",
        description = "Show workflow guidance for AI coding agents",
        params = listOf(SECTION),
        requiresCompilation = false,
        category = TaskCategory.HELP,
    )

    val FIND_STRING_CONSTANT = TaskDef(
        goal = "find-string-constant",
        description = "Search string constants in compiled code matching a regex",
        params = FORMAT_PARAMS + STRING_PATTERN,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val ANNOTATIONS = TaskDef(
        goal = "annotations",
        description = "Find classes and methods by annotation pattern",
        params = FORMAT_PARAMS + listOf(PATTERN, METHODS),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val CONFIG_HELP = TaskDef(
        goal = "config-help",
        description = "Show configuration reference for all parameters",
        params = emptyList(),
        requiresCompilation = false,
        category = TaskCategory.HELP,
        legacyGradleTaskName = "cnavHelpConfig",
    )

    val CHANGED_SINCE = TaskDef(
        goal = "changed-since",
        description = "Show blast radius of changes since a git ref (changed classes and their callers)",
        params = FORMAT_PARAMS + listOf(REF, PROJECTONLY_ON),
        requiresCompilation = true,
        category = TaskCategory.HYBRID,
    )

    val CONTEXT = TaskDef(
        goal = "context",
        description = "Gather full context for a class: detail, callers, callees, interfaces",
        params = FORMAT_PARAMS + listOf(PATTERN, CONTEXT_MAXDEPTH, PROJECTONLY_ON, FILTER_SYNTHETIC, PROD_ONLY, TEST_ONLY),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
    )

    val ALL_TASKS: List<TaskDef> = listOf(
        LIST_CLASSES,
        FIND_CLASS,
        FIND_SYMBOL,
        CLASS_DETAIL,
        FIND_CALLERS,
        FIND_CALLEES,
        FIND_INTERFACES,
        TYPE_HIERARCHY,
        PACKAGE_DEPS,
        DSM,
        CYCLE_DETECTION,
        FIND_USAGES,
        RANK,
        DEAD,
        FIND_STRING_CONSTANT,
        ANNOTATIONS,
        COMPLEXITY,
        METRICS,
        HOTSPOTS,
        CHURN,
        CODE_AGE,
        AUTHORS,
        COUPLING,
        CHANGED_SINCE,
        CONTEXT,
        HELP,
        AGENT_HELP,
        CONFIG_HELP,
    )
}
