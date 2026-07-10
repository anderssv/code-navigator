package no.f12.codenavigator.registry

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.PatternEnhancer
import no.f12.codenavigator.navigation.types.FrameworkPresets
import java.time.LocalDate

enum class TaskCategory {
    NAVIGATION,
    GIT_HISTORY,
    HYBRID,
    COMPOSITE,
    SOURCE,
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

    data object DOUBLE : ParamType<Double>(
        parse = { value, defaultValue ->
            value?.toDoubleOrNull() ?: defaultValue?.toDoubleOrNull() ?: 0.0
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
    val deprecated: Boolean = false,
    val deprecatedMessage: String? = null,
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

    fun parseRequiredFrom(properties: Map<String, String?>): T & Any =
        parseFrom(properties) ?: throw IllegalArgumentException("Missing required property '$name'")

    companion object {
        fun parseFormat(properties: Map<String, String?>): OutputFormat =
            OutputFormat.from(
                format = properties["format"] ?: if (properties["llm"]?.toBoolean() == true) "llm" else null,
            )
    }
}

data class UsageExample(
    val params: List<Pair<ParamDef<*>, String?>>,
) {
    fun render(goal: String, tool: BuildTool): String {
        val parts = params.map { (param, value) ->
            if (value == null) tool.paramFlag(param.name) else tool.param(param.name, value)
        }
        return (listOf(tool.command, tool.taskName(goal)) + parts).joinToString(" ")
    }
}

data class TaskDef(
    val goal: String,
    val description: String,
    val params: List<ParamDef<*>>,
    val requiresCompilation: Boolean,
    val category: TaskCategory,
    val legacyGradleTaskName: String? = null,
    val requiresTestCompilation: Boolean = false,
    val paramDefaultOverrides: Map<String, String> = emptyMap(),
    val aliases: List<String> = emptyList(),
    val examples: List<UsageExample> = emptyList(),
    /** Short intent phrase for "I want to..." lists (e.g. "Change method parameters"). Only for SOURCE tasks. */
    val intent: String? = null,
    /** Brief explanation of what the task does for summaries (e.g. "add/remove/reorder params, updates call sites"). */
    val intentDetail: String? = null,
) {
    init {
        val paramNames = params.map { it.name }.toSet()
        for ((index, example) in examples.withIndex()) {
            val unknownParams = example.params.map { it.first.name }.filter { it !in paramNames }
            require(unknownParams.isEmpty()) {
                "Task '$goal' example #${index + 1} references unknown params: $unknownParams. Known: ${paramNames.sorted()}"
            }
        }
    }

    val gradleTaskName: String = goalToGradleTaskName(goal)
    val aliasGradleTaskNames: List<String> = aliases.map { goalToGradleTaskName(it) }

    fun effectiveDefault(param: ParamDef<*>): String? =
        paramDefaultOverrides[param.name] ?: param.defaultValue

    fun taskName(tool: BuildTool): String = tool.taskName(goal)

    fun usageHint(tool: BuildTool): String {
        val visibleParams = params.filter { !it.deprecated && it.name != "format" && it.name != "llm" }
        val parts = visibleParams.map { param ->
            val rendered = param.render(tool)
            if (param.defaultValue == null && !param.flag) rendered else "[$rendered]"
        }
        return "Usage: ${tool.command} ${taskName(tool)}${if (parts.isEmpty()) "" else " ${parts.joinToString(" ")}"}"
    }

    fun paramByName(name: String): ParamDef<*> =
        params.first { it.name == name }

    fun renderExamples(tool: BuildTool): List<String> =
        examples.map { "Usage: ${it.render(goal, tool)}" }

    fun deprecations(properties: Map<String, String?>): List<String> =
        params
            .filter { it.deprecated && properties.containsKey(it.name) }
            .mapNotNull { it.deprecatedMessage }

    fun warnUnsupportedProperties(availablePropertyNames: Set<String>): List<String> {
        val myParamNames = params.map { it.name }.toSet()
        val allCnavParamNames = TaskRegistry.ALL_TASKS.flatMap { it.params }.map { it.name }.toSet()
        val unsupported = availablePropertyNames
            .filter { it in allCnavParamNames && it !in myParamNames }
            .sorted()
        return unsupported.map { name ->
            "Parameter '$name' is not supported by task '$goal'.\n${usageHint(BuildTool.GRADLE)}"
        }
    }

    fun warnUnknownProperties(availablePropertyNames: Set<String>): List<String> {
        val allCnavParamNames = TaskRegistry.ALL_TASKS.flatMap { it.params }.map { it.name }.toSet()
        val unknown = availablePropertyNames
            .filter { it !in allCnavParamNames && !it.startsWith("org.gradle.") && !it.startsWith("android.") }
            .sorted()
        return unknown.map { name ->
            "Unknown parameter '$name' is not recognized by any code-navigator task.\n${usageHint(BuildTool.GRADLE)}"
        }
    }

    fun enhanceProperties(properties: Map<String, String?>): Map<String, String?> {
        val knownNames = params.map { it.name }.toSet()
        val unknown = properties.keys - knownNames
        require(unknown.isEmpty()) {
            "Task '$goal' received unknown properties: ${unknown.sorted()}. Known: ${knownNames.sorted()}"
        }
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
    val LLM = ParamDef("llm", "true", "Deprecated: use --format=llm instead", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN, deprecated = true, deprecatedMessage = "'llm' is deprecated. Use 'format=llm' instead (Gradle: --format=llm, Maven: -Dformat=llm).")
    val PATTERN = ParamDef("pattern", "<regex>", "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
    val CALL_PATTERN = ParamDef("pattern", "<regex>", "Class.method name regex (camelCase-aware: MyService.doWork matches com.example.MyService.doWork)", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
    val LEGACY_METHOD = ParamDef("method", "<regex>", "Deprecated: use pattern instead", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING, deprecated = true, deprecatedMessage = "'method' is deprecated for find-callers/find-callees. Use 'pattern' instead (Gradle: --pattern=MyClass.myMethod, Maven: -Dpattern=MyClass.myMethod).")
    val METHOD = ParamDef("method", "<regex>", "Method name regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val MAXDEPTH = ParamDef("maxdepth", "<N>", "Max call tree depth", flag = false, defaultValue = "3", enhancePattern = false, type = ParamType.INT)
    val MAX_IMPLEMENTORS = ParamDef("max-implementors", "<N>", "Max polymorphic implementors to expand per interface call site before collapsing the rest into a '+N more' note", flag = false, defaultValue = "5", enhancePattern = false, type = ParamType.INT)
    val PROJECTONLY = ParamDef("project-only", "false", "Hide JDK/stdlib/library classes (default: on)", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)
    val FILTER_SYNTHETIC = ParamDef("filter-synthetic", "false", "Set false to include synthetic methods (equals, hashCode, copy, componentN, etc.)", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)
    val TOP = ParamDef("top", "<N>", "Max results", flag = false, defaultValue = "50", enhancePattern = false, type = ParamType.INT)
    val MIN_METHODS = ParamDef("min-methods", "<N>", "Minimum eligible method count to include a class", flag = false, defaultValue = "0", enhancePattern = false, type = ParamType.INT)
    val MIN_TCC = ParamDef("min-tcc", "<0.0-1.0>", "Minimum Tight Class Cohesion to include a class", flag = false, defaultValue = "0.0", enhancePattern = false, type = ParamType.DOUBLE)
    val MAX_WMC = ParamDef("max-wmc", "<N>", "Maximum WMC (summed cyclomatic complexity) to include a class", flag = false, defaultValue = "${Int.MAX_VALUE}", enhancePattern = false, type = ParamType.INT)
    val MAX_CBO = ParamDef("max-cbo", "<N>", "Maximum CBO (coupling between objects) to include a class", flag = false, defaultValue = "${Int.MAX_VALUE}", enhancePattern = false, type = ParamType.INT)
    val MULTI_MODULE = ParamDef("multi-module", "true", "Aggregate class directories from this project's real project dependencies, transitively (Gradle only). Unrelated sibling modules are excluded even when the plugin is applied there.", flag = false, defaultValue = "false", enhancePattern = false, type = ParamType.BOOLEAN)
    val OVER = ParamDef("over", "<N>", "Only show files over N lines", flag = false, defaultValue = "0", enhancePattern = false, type = ParamType.INT)
    val AFTER = ParamDef("after", "YYYY-MM-DD", "Only consider commits after this date", flag = false, defaultValue = "1 year ago", enhancePattern = false, type = ParamType.DATE)
    val NO_FOLLOW = ParamDef("no-follow", "", "Disable git rename tracking", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)
    val MIN_REVS = ParamDef("min-revs", "<N>", "Min revisions to include", flag = false, defaultValue = "1", enhancePattern = false, type = ParamType.INT)

    // --- Task-specific parameter definitions ---

    val INCLUDETEST = ParamDef("include-test", "true", "Deprecated: test sources are now included by default. Use scope=prod to see only production code.", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN, deprecated = true, deprecatedMessage = "'include-test' is deprecated. Test sources are now included by default. Use 'scope=prod' to see only production code.")
    val PACKAGE = ParamDef("package", "<regex>", "Filter packages by regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val REVERSE = ParamDef("reverse", "true", "Show reverse dependencies", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val ROOT_PACKAGE = ParamDef("root-package", "<pkg>", "Deprecated: use package-filter instead. Only include packages under this prefix", flag = false, defaultValue = "all", enhancePattern = false, type = ParamType.STRING, deprecated = true, deprecatedMessage = "'root-package' is deprecated. Results are now automatically limited to classes in the project source sets. Use 'package-filter' to narrow further.")
    val PACKAGE_FILTER = ParamDef("package-filter", "<pkg>", "Only include packages under this prefix", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val MIN_EDGES = ParamDef("min-edges", "<N>", "Minimum total edges (internal+external) to include a package", flag = false, defaultValue = "0", enhancePattern = false, type = ParamType.INT)
    val FAIL_ON_VIOLATION = ParamDef("fail-on-violation", "true", "Fail the build when violations exceed the configured threshold", flag = false, defaultValue = "false", enhancePattern = false, type = ParamType.BOOLEAN)
    val MAX_CYCLES = ParamDef("max-cycles", "<N>", "Max allowed cycles before failing the build (used with --fail-on-violation)", flag = false, defaultValue = "0", enhancePattern = false, type = ParamType.INT)
    val MAX_VIOLATIONS = ParamDef("max-violations", "<N>", "Max allowed ring violations before failing the build (used with --fail-on-violation)", flag = false, defaultValue = "0", enhancePattern = false, type = ParamType.INT)
    val MAX_FAN_IN = ParamDef("max-fan-in", "<N>", "Exclude ubiquitous types with fan-in above this threshold from move suggestions", flag = false, defaultValue = "10", enhancePattern = false, type = ParamType.INT)
    val MIN_GROUP_SIZE = ParamDef("min-group-size", "<N>", "Minimum number of classes in a structure group", flag = false, defaultValue = "2", enhancePattern = false, type = ParamType.INT)
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
    val SCOPE = ParamDef("scope", "all|prod|test", "Filter by source set: all (default), prod (production only), test (test only)", flag = false, defaultValue = "all", enhancePattern = false, type = ParamType.STRING)
    val PLAN_FILE = ParamDef("plan-file", "<path>", "JSON file with refactoring steps (simulates moves for analysis tasks, executes them for execute-plan)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val GROUP_BY = ParamDef("group-by", "none|file", "Group results: none (default, per-reference) or file (collapse to one line per source file with count)", flag = false, defaultValue = "none", enhancePattern = false, type = ParamType.STRING)
    val RAW = ParamDef("raw", "", "Show raw bytecode-level output without collapsing", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)
    val INCLUDE_IMPLS = ParamDef("include-impls", "", "When target is an interface, also search usages of implementors", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)
    val TREAT_AS_DEAD = ParamDef("treat-as-dead", "<name>", "Treat framework-annotated code as potentially dead (all frameworks protected by default). Available: ${FrameworkPresets.availablePresets().sorted().joinToString(", ")}. Use ALL to remove all framework protections.", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.LIST_STRING)
    val BASELINE = ParamDef("baseline", "<path>", "Path to a previous cnavDead JSON output file. Shows diff: removed, remaining, and new dead code since baseline.", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val MIN_CONFIDENCE = ParamDef("min-confidence", "high|medium|low", "Only show findings at or above this confidence level (high = strongest removal candidates, low = include framework-invoked maybes). Default: low (show all).", flag = false, defaultValue = "low", enhancePattern = false, type = ParamType.STRING)
    val INCLUDE_SUPPRESSED = ParamDef("include-suppressed", "", "Include findings annotated with @Suppress(\"unused\") (excluded by default)", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)
    val DETAIL = ParamDef("detail", "true", "Show individual call details", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val COLLAPSE_LAMBDAS = ParamDef("collapse-lambdas", "false", "Set false to show lambda classes separately", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)
    val MIN_SHARED_REVS = ParamDef("min-shared-revs", "<N>", "Min shared commits", flag = false, defaultValue = "5", enhancePattern = false, type = ParamType.INT)
    val MIN_COUPLING = ParamDef("min-coupling", "<N>", "Min coupling degree %", flag = false, defaultValue = "30", enhancePattern = false, type = ParamType.INT)
    val MAX_CHANGESET_SIZE = ParamDef("max-changeset-size", "<N>", "Skip commits touching more files", flag = false, defaultValue = "30", enhancePattern = false, type = ParamType.INT)
    val METRICS_TOP = ParamDef("top", "<N>", "Max results per section", flag = false, defaultValue = "5", enhancePattern = false, type = ParamType.INT)
    val SECTION = ParamDef("section", "<name>", "Help section: install, workflow, interpretation, schemas, extraction", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val TOPIC = ParamDef("topic", "<name>", "Philosophy topic: hexagonal, tttd, fakes, manual-di", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val JAR = ParamDef("jar", "<path-or-artifact>", "Scan a JAR file instead of project classes. Value: file path or artifact coordinate (group:name)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val REF = ParamDef("ref", "<git-ref>", "Git ref to compare against (branch, tag, or commit SHA)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val STRING_PATTERN = ParamDef("pattern", "<regex>", "Regex to match against string constant values", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val METHODS = ParamDef("methods", "true", "Deprecated: class, method, and field annotations are all searched by default now", flag = false, defaultValue = "false", enhancePattern = false, type = ParamType.BOOLEAN, deprecated = true, deprecatedMessage = "'methods' is deprecated. All targets (class, method, field) are searched by default now. Use 'target=class,method,field' to filter to specific targets.")
    val TARGET = ParamDef("target", "class,method,field", "Annotation targets to search (default: all)", flag = false, defaultValue = "class,method,field", enhancePattern = false, type = ParamType.LIST_STRING)
    val CONTEXT_MAXDEPTH = ParamDef("maxdepth", "<N>", "Max call tree depth (default: 2)", flag = false, defaultValue = "2", enhancePattern = false, type = ParamType.INT)
    val RENAME_CLASS = ParamDef("target-class", "<fqcn>", "Fully qualified class name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val RENAME_METHOD = ParamDef("method", "<name>", "Method name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val RENAME_PARAM = ParamDef("param", "<name>", "Current parameter name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val RENAME_PROPERTY = ParamDef("property", "<name>", "Current property name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val RENAME_NEW_NAME = ParamDef("new-name", "<name>", "New name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val PREVIEW = ParamDef("preview", "true", "Preview changes without writing to source files", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)
    val CHANGE_SIG_PARAMS = ParamDef("params", "<params>", "New parameter list (e.g. \"limit: Int, offset: Int, query: String\")", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val CHANGE_SIG_DEFAULTS = ParamDef("defaults", "<defaults>", "Default values for new params at call sites (e.g. \"query=\\\"\\\"\") comma-separated name=value pairs", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val MIN_TOKENS = ParamDef("min-tokens", "<N>", "Minimum duplicate token sequence length", flag = false, defaultValue = "50", enhancePattern = false, type = ParamType.INT)
    val MOVE_FROM = ParamDef("from", "<fqcn>", "Fully qualified class name to move/rename", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val MOVE_TO = ParamDef("to", "<fqcn>", "Target fully qualified class name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val FROM_FILE = ParamDef("from-file", "<path>", "Relative path to the source file to move (e.g. src/main/kotlin/com/example/Foo.kt)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val FROM_PACKAGE = ParamDef("from-package", "<pkg>", "Source package (dot-separated)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val TO_PACKAGE = ParamDef("to-package", "<pkg>", "Target package (dot-separated)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val PORTS = ParamDef("ports", "<regex>", "Regex matching port interface names (hexagonal boundaries that get faked in tests, e.g. .*Repository|.*Client)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val AFFINITY_THRESHOLD = ParamDef("threshold", "<N>", "Max number of consumer domains to still count as single-owner", flag = false, defaultValue = "1", enhancePattern = false, type = ParamType.INT)
    val RING_MODE = ParamDef("mode", "emergent|package", "Analysis mode: emergent (default, assigns rings per class based on import shape — best for package-by-feature) or package (assigns rings per package by topological depth)", flag = false, defaultValue = "emergent", enhancePattern = false, type = ParamType.STRING)
    val BOOTSTRAP_CONFIG = ParamDef("bootstrap-config", "true", "Generate a starting cnav-config.json based on emergent ring analysis — best-effort suggestions meant to be reviewed and tweaked before use", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)
    val CONVERGE_MODE = ParamDef("mode", "intersect|risk", "Analysis mode: intersect (default, cross-references cycles/rings/change-coupling for a ranked ACT NOW/LATENT/MISSING ABSTRACTION list) or risk (change-frequency x complexity x coupling ranking)", flag = false, defaultValue = "intersect", enhancePattern = false, type = ParamType.STRING)

    val FORMAT_PARAMS = listOf(FORMAT)
    val SOURCE_SET_PARAMS = listOf(SCOPE)
    val PLAN_PARAMS = listOf(PLAN_FILE)

    // --- Task definitions ---

    val LIST_CLASSES = TaskDef(
        goal = "list-classes",
        description = "List all classes in the project",
        params = FORMAT_PARAMS + listOf(PATTERN, JAR) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(PATTERN to "Service")),
            UsageExample(listOf(JAR to "/path/to/lib.jar")),
            UsageExample(listOf(JAR to "com.example:my-lib")),
        ),
    )

    val FIND_CLASS = TaskDef(
        goal = "find-class",
        description = "Find classes matching a regex pattern",
        params = FORMAT_PARAMS + PATTERN + listOf(JAR) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(PATTERN to "Service")),
            UsageExample(listOf(PATTERN to "\".*Reset.*\"")),
            UsageExample(listOf(PATTERN to "\"domain\\\\..*\"")),
            UsageExample(listOf(PATTERN to "Service", JAR to "/path/to/lib.jar")),
        ),
    )

    val FIND_SYMBOL = TaskDef(
        goal = "find-symbol",
        description = "Find methods and fields matching a regex pattern",
        params = FORMAT_PARAMS + listOf(PATTERN, JAR) + SOURCE_SET_PARAMS + listOf(INCLUDETEST),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(PATTERN to "resetPassword")),
            UsageExample(listOf(PATTERN to "\".*Service.*\"")),
            UsageExample(listOf(PATTERN to "\"find.*\"")),
            UsageExample(listOf(PATTERN to "find", JAR to "com.example:my-lib")),
        ),
    )

    val CLASS_DETAIL = TaskDef(
        goal = "class-detail",
        description = "Show detailed class information (methods, fields, interfaces)",
        params = FORMAT_PARAMS + PATTERN + listOf(JAR) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavClass",
        examples = listOf(
            UsageExample(listOf(PATTERN to "ResetPasswordService")),
            UsageExample(listOf(PATTERN to "\".*Client\"")),
            UsageExample(listOf(PATTERN to "\"domain\\\\.DomainError\"")),
            UsageExample(listOf(PATTERN to "MyClient", JAR to "com.example:client-sdk")),
        ),
    )

    val FIND_CALLERS = TaskDef(
        goal = "find-callers",
        description = "Full call hierarchy: find all callers of a method, recursively",
        params = FORMAT_PARAMS + listOf(CALL_PATTERN, LEGACY_METHOD, MAXDEPTH, PROJECTONLY, FILTER_SYNTHETIC, SCOPE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavCallers",
        examples = listOf(
            UsageExample(listOf(CALL_PATTERN to "resetPassword", MAXDEPTH to "3")),
            UsageExample(listOf(CALL_PATTERN to "\".*Service\\\\.find.*\"", MAXDEPTH to "5")),
            UsageExample(listOf(CALL_PATTERN to "\"Repo\\\\.save\"", MAXDEPTH to "5", PROJECTONLY to "true")),
        ),
    )

    val FIND_CALLEES = TaskDef(
        goal = "find-callees",
        description = "Full call hierarchy: find all methods called by a method, recursively",
        params = FORMAT_PARAMS + listOf(CALL_PATTERN, LEGACY_METHOD, MAXDEPTH, PROJECTONLY, FILTER_SYNTHETIC, SCOPE, MAX_IMPLEMENTORS),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavCallees",
        examples = listOf(
            UsageExample(listOf(CALL_PATTERN to "resetPassword", MAXDEPTH to "3")),
            UsageExample(listOf(CALL_PATTERN to "\".*Controller\\\\.handle.*\"", MAXDEPTH to "5")),
            UsageExample(listOf(CALL_PATTERN to "\"Service\\\\.create\"", MAXDEPTH to "5", PROJECTONLY to "true")),
            UsageExample(listOf(CALL_PATTERN to "\"Repository\\\\.save\"", MAX_IMPLEMENTORS to "10")),
        ),
    )

    val FIND_INTERFACES = TaskDef(
        goal = "find-interfaces",
        description = "Find implementations of an interface",
        params = FORMAT_PARAMS + listOf(PATTERN) + SOURCE_SET_PARAMS + listOf(INCLUDETEST),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavInterfaces",
        examples = listOf(
            UsageExample(listOf(PATTERN to "Repository")),
            UsageExample(listOf(PATTERN to "\".*Client\"")),
            UsageExample(listOf(PATTERN to "\"services\\\\.interfaces\\\\..*\"")),
            UsageExample(listOf(PATTERN to "Repository", SCOPE to "test")),
        ),
    )

    val TYPE_HIERARCHY = TaskDef(
        goal = "type-hierarchy",
        description = "Show type hierarchy (supertypes upward, implementors downward)",
        params = FORMAT_PARAMS + listOf(PATTERN, PROJECTONLY) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(PATTERN to "Service")),
            UsageExample(listOf(PATTERN to "\".*Repository\"")),
            UsageExample(listOf(PATTERN to "\"domain\\\\..*\"", PROJECTONLY to "true")),
        ),
    )

    val PACKAGE_DEPS = TaskDef(
        goal = "package-deps",
        description = "Show package-level dependencies",
        params = FORMAT_PARAMS + listOf(PACKAGE, PROJECTONLY, REVERSE) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavDeps",
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(PACKAGE to "services")),
            UsageExample(listOf(PACKAGE to "\"ktor\\\\.routes\"")),
            UsageExample(listOf(REVERSE to "true")),
            UsageExample(listOf(PACKAGE to "domain", REVERSE to "true")),
            UsageExample(listOf(PROJECTONLY to "true")),
        ),
    )

    val WHY_DEPENDS = TaskDef(
        goal = "why-depends",
        description = "Show why one package depends on another at class level",
        params = FORMAT_PARAMS + listOf(FROM_PACKAGE, TO_PACKAGE) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(FROM_PACKAGE to "com.example.api", TO_PACKAGE to "com.example.db")),
        ),
    )

    val DSM = TaskDef(
        goal = "dsm",
        description = "Generate Dependency Structure Matrix",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, DSM_HTML, CYCLES, CYCLE, ROOT_PACKAGE, MULTI_MODULE) + SOURCE_SET_PARAMS + PLAN_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(PACKAGE_FILTER to "com.example.api", DSM_DEPTH to "3")),
            UsageExample(listOf(INCLUDE_EXTERNAL to "true", PACKAGE_FILTER to "com.example")),
            UsageExample(listOf(DSM_HTML to "build/dsm.html")),
            UsageExample(listOf(CYCLES to "true")),
            UsageExample(listOf(CYCLE to "api,service")),
            UsageExample(listOf(MULTI_MODULE to "true")),
        ),
    )

    val CYCLE_DETECTION = TaskDef(
        goal = "cycles",
        description = "Detect dependency cycles using Tarjan's SCC algorithm. Supports --fail-on-violation for CI gating.",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, ROOT_PACKAGE, FAIL_ON_VIOLATION, MAX_CYCLES) + SOURCE_SET_PARAMS + PLAN_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(PACKAGE_FILTER to "com.example", DSM_DEPTH to "3")),
            UsageExample(listOf(FAIL_ON_VIOLATION to "true", MAX_CYCLES to "0")),
        ),
    )

    val SIMULATE_MOVE = TaskDef(
        goal = "simulate-move",
        description = "Predict cycle impact of moving a class to a different package without modifying code",
        params = FORMAT_PARAMS + listOf(TYPE, TO_PACKAGE, PACKAGE_FILTER, DSM_DEPTH, ROOT_PACKAGE) + SOURCE_SET_PARAMS + PLAN_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(TYPE to "RedisCache", TO_PACKAGE to "com.example.infrastructure")),
        ),
    )

    val FIND_USAGES = TaskDef(
        goal = "find-usages",
        description = "Find project references to types, methods, and fields/properties",
        params = FORMAT_PARAMS + listOf(OWNER_CLASS, METHOD, FIELD, TYPE, OUTSIDE_PACKAGE, FILTER_SYNTHETIC, SCOPE, GROUP_BY, RAW, INCLUDE_IMPLS),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavUsages",
        examples = listOf(
            UsageExample(listOf(OWNER_CLASS to "kotlinx.datetime.LocalDate", METHOD to "getMonthNumber")),
            UsageExample(listOf(OWNER_CLASS to "kotlinx.datetime.Clock")),
            UsageExample(listOf(OWNER_CLASS to "com.example.Config", FIELD to "timeout")),
            UsageExample(listOf(TYPE to "kotlinx.datetime.Instant")),
            UsageExample(listOf(TYPE to "kotlinx.datetime.Instant", OUTSIDE_PACKAGE to "com.example.datetime")),
            UsageExample(listOf(TYPE to "com.example.SignatureContext", GROUP_BY to "file")),
        ),
    )

    val RANK = TaskDef(
        goal = "rank",
        description = "Rank types by importance (PageRank on call graph)",
        params = FORMAT_PARAMS + listOf(TOP, PROJECTONLY, COLLAPSE_LAMBDAS, SCOPE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(TOP to "20")),
            UsageExample(listOf(PROJECTONLY to "false")),
        ),
    )

    val DEAD = TaskDef(
        goal = "dead",
        description = "Detect dead code (unreferenced classes and methods)",
        params = FORMAT_PARAMS + listOf(FILTER, EXCLUDE, CLASSES_ONLY, EXCLUDE_ANNOTATED, SCOPE, TREAT_AS_DEAD, BASELINE, MIN_CONFIDENCE, INCLUDE_SUPPRESSED),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        requiresTestCompilation = true,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(FILTER to "\"service\"")),
            UsageExample(listOf(EXCLUDE to "\"Main|Test|Application\"")),
            UsageExample(listOf(EXCLUDE to "\"com\\\\.example\\\\.grpc\"")),
            UsageExample(listOf(CLASSES_ONLY to "true")),
            UsageExample(listOf(EXCLUDE_ANNOTATED to "\"RestController,Scheduled\"")),
            UsageExample(listOf(SCOPE to "prod")),
            UsageExample(listOf(TREAT_AS_DEAD to "spring")),
        UsageExample(listOf(TREAT_AS_DEAD to "ALL")),
        UsageExample(listOf(BASELINE to "build/cnav/dead-baseline.json")),
        UsageExample(listOf(MIN_CONFIDENCE to "high")),
        UsageExample(listOf(INCLUDE_SUPPRESSED to "true")),
        ),
    )

    val HOTSPOTS = TaskDef(
        goal = "hotspots",
        description = "Rank files by change frequency",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_REVS, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(AFTER to "2024-01-01", MIN_REVS to "5", TOP to "20")),
        ),
    )

    val CHURN = TaskDef(
        goal = "churn",
        description = "Show code churn (lines added/deleted per file)",
        params = FORMAT_PARAMS + listOf(AFTER, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(AFTER to "2024-06-01", TOP to "30")),
        ),
    )

    val CODE_AGE = TaskDef(
        goal = "code-age",
        description = "Show time since last modification per file",
        params = FORMAT_PARAMS + listOf(AFTER, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
        legacyGradleTaskName = "cnavAge",
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(AFTER to "2023-01-01", TOP to "20")),
        ),
    )

    val AUTHORS = TaskDef(
        goal = "authors",
        description = "Show number of distinct contributors per file",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_REVS, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(MIN_REVS to "3", TOP to "20")),
        ),
    )

    val COUPLING = TaskDef(
        goal = "coupling",
        description = "Find files that change together (temporal coupling)",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_SHARED_REVS, MIN_COUPLING, MAX_CHANGESET_SIZE, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(MIN_SHARED_REVS to "10", MIN_COUPLING to "50")),
        ),
    )

    val COMPLEXITY = TaskDef(
        goal = "complexity",
        description = "Show fan-in/fan-out complexity per class",
        params = FORMAT_PARAMS + listOf(PATTERN, PROJECTONLY, DETAIL, COLLAPSE_LAMBDAS, TOP, SCOPE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(PATTERN to "Service")),
            UsageExample(listOf(PATTERN to "\".*Controller\"", PROJECTONLY to "false")),
            UsageExample(listOf(PATTERN to "\"domain\\\\..*\"", DETAIL to "true")),
        ),
    )

    val CLASS_METRICS = TaskDef(
        goal = "class-metrics",
        description = "Per-class cohesion (TCC/LCC) and complexity (WMC/CBO/DIT) metrics",
        params = FORMAT_PARAMS + listOf(MIN_METHODS, MIN_TCC, MAX_WMC, MAX_CBO) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(MIN_METHODS to "5")),
            UsageExample(listOf(MIN_TCC to "0.0", MAX_WMC to "20")),
        ),
    )

    val METRICS = TaskDef(
        goal = "metrics",
        description = "Quick project health snapshot: classes, packages, fan-in/out, cycles, dead code, hotspots",
        params = FORMAT_PARAMS + listOf(AFTER, METRICS_TOP, NO_FOLLOW, PACKAGE_FILTER, INCLUDE_EXTERNAL, EXCLUDE_ANNOTATED, TREAT_AS_DEAD, ROOT_PACKAGE) + SOURCE_SET_PARAMS + PLAN_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(METRICS_TOP to "10", AFTER to "2024-01-01")),
            UsageExample(listOf(PACKAGE_FILTER to "com.example")),
        ),
    )

    val HELP = TaskDef(
        goal = "help",
        description = "Show help text with available tasks",
        params = emptyList(),
        requiresCompilation = false,
        category = TaskCategory.HELP,
        examples = listOf(
            UsageExample(emptyList()),
        ),
    )

    val AGENT_HELP = TaskDef(
        goal = "agent-help",
        description = "Show workflow guidance for AI coding agents",
        params = listOf(SECTION, TOPIC),
        requiresCompilation = false,
        category = TaskCategory.HELP,
        examples = listOf(
            UsageExample(emptyList()),
        ),
    )

    val FIND_STRING_CONSTANT = TaskDef(
        goal = "find-string-constant",
        description = "Search string constants in compiled code matching a regex",
        params = FORMAT_PARAMS + STRING_PATTERN + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(STRING_PATTERN to "\"http://\"")),
            UsageExample(listOf(STRING_PATTERN to "\"SELECT.*FROM\"")),
            UsageExample(listOf(STRING_PATTERN to "\"password|secret|key\"")),
        ),
    )

    val ANNOTATIONS = TaskDef(
        goal = "annotations",
        description = "Find classes, methods, and fields by annotation pattern",
        params = FORMAT_PARAMS + listOf(PATTERN, METHODS, TARGET) + SOURCE_SET_PARAMS + listOf(INCLUDETEST),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(PATTERN to "RestController")),
            UsageExample(listOf(PATTERN to "\".*Mapping\"")),
            UsageExample(listOf(PATTERN to "Inject", TARGET to "field")),
        ),
    )

    val CONFIG_HELP = TaskDef(
        goal = "config-help",
        description = "Show configuration reference for all parameters",
        params = emptyList(),
        requiresCompilation = false,
        category = TaskCategory.HELP,
        legacyGradleTaskName = "cnavHelpConfig",
        examples = listOf(
            UsageExample(emptyList()),
        ),
    )

    val CHANGED_SINCE = TaskDef(
        goal = "changed-since",
        description = "Show blast radius of changes since a git ref (changed classes and their callers)",
        params = FORMAT_PARAMS + listOf(REF, PROJECTONLY) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.HYBRID,
        examples = listOf(
            UsageExample(listOf(REF to "main")),
            UsageExample(listOf(REF to "v1.2.0")),
            UsageExample(listOf(REF to "HEAD~5")),
        ),
    )

    val CONTEXT = TaskDef(
        goal = "context",
        description = "Gather full context for a class: detail, callers, callees, interfaces",
        params = FORMAT_PARAMS + listOf(PATTERN, CONTEXT_MAXDEPTH, PROJECTONLY, FILTER_SYNTHETIC, SCOPE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(PATTERN to "ResetPasswordService")),
            UsageExample(listOf(PATTERN to "\".*Controller\"", CONTEXT_MAXDEPTH to "3")),
            UsageExample(listOf(PATTERN to "MyService", FORMAT to "json")),
        ),
    )

    val DISTANCE = TaskDef(
        goal = "distance",
        description = "Compute structural distance between coupled packages",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, TOP) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        paramDefaultOverrides = mapOf("top" to "all"),
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(TOP to "20")),
            UsageExample(listOf(PACKAGE_FILTER to "com.example")),
        ),
    )

    val STRENGTH = TaskDef(
        goal = "strength",
        description = "Classify integration strength of inter-package dependencies",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, TOP) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        paramDefaultOverrides = mapOf("top" to "all"),
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(TOP to "20")),
            UsageExample(listOf(PACKAGE_FILTER to "com.example")),
        ),
    )

    val COHESION = TaskDef(
        goal = "cohesion",
        description = "Score package cohesion: ratio of internal to total class dependencies per package",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, TOP, MIN_EDGES) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        paramDefaultOverrides = mapOf("top" to "all"),
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(TOP to "10")),
            UsageExample(listOf(MIN_EDGES to "5")),
            UsageExample(listOf(PACKAGE_FILTER to "com.example")),
        ),
    )

    val MOVE_SUGGEST = TaskDef(
        goal = "move-suggest",
        description = "Suggest misplaced classes based on dependency gravity — classes with more edges to another package than their own",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, TOP, MAX_FAN_IN) + SOURCE_SET_PARAMS + PLAN_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        paramDefaultOverrides = mapOf("top" to "all"),
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(TOP to "10")),
            UsageExample(listOf(MAX_FAN_IN to "5")),
            UsageExample(listOf(PACKAGE_FILTER to "com.example")),
            UsageExample(listOf(PLAN_FILE to "plan.json")),
        ),
    )

    val SUGGEST_STRUCTURE = TaskDef(
        goal = "suggest-structure",
        description = "Group misplaced classes by target package — shows which classes should move together and computes structural drift",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, TOP, MAX_FAN_IN, MIN_GROUP_SIZE) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        paramDefaultOverrides = mapOf("top" to "all"),
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(MIN_GROUP_SIZE to "3")),
            UsageExample(listOf(PACKAGE_FILTER to "com.example")),
        ),
    )

    val VOLATILITY = TaskDef(
        goal = "volatility",
        description = "Show package-level volatility from git history (change frequency and churn)",
        params = FORMAT_PARAMS + listOf(AFTER, MIN_REVS, TOP, NO_FOLLOW),
        requiresCompilation = false,
        category = TaskCategory.GIT_HISTORY,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(AFTER to "2024-01-01", TOP to "20")),
        ),
    )

    val BALANCE = TaskDef(
        goal = "balance",
        description = "Composite balanced coupling analysis: strength × distance × volatility",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, TOP, AFTER, MIN_REVS, NO_FOLLOW) + SOURCE_SET_PARAMS + PLAN_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.COMPOSITE,
        paramDefaultOverrides = mapOf("top" to "all"),
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(PACKAGE_FILTER to "com.example")),
            UsageExample(listOf(TOP to "20")),
        ),
    )

    val RINGS = TaskDef(
        goal = "rings",
        description = "Auto-detect hexagonal architecture rings and report violations. Use --mode=emergent for class-level ring detection based on import shapes. Supports --fail-on-violation for CI gating.",
        params = FORMAT_PARAMS + SOURCE_SET_PARAMS + listOf(RING_MODE, BOOTSTRAP_CONFIG, FAIL_ON_VIOLATION, MAX_VIOLATIONS) + PLAN_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(RING_MODE to "emergent")),
            UsageExample(listOf(FAIL_ON_VIOLATION to "true", MAX_VIOLATIONS to "0")),
        ),
    )

    val TYPE_AFFINITY = TaskDef(
        goal = "type-affinity",
        description = "Find types in a shared package that are exclusively used by one feature — candidates to move closer to their consumer",
        params = FORMAT_PARAMS + listOf(PACKAGE, AFFINITY_THRESHOLD) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(PACKAGE to "com.example.domain")),
            UsageExample(listOf(PACKAGE to "com.example.domain", AFFINITY_THRESHOLD to "2")),
        ),
    )

    val SIZE = TaskDef(
        goal = "size",
        description = "List source files by line count",
        params = FORMAT_PARAMS + listOf(TOP, OVER),
        requiresCompilation = false,
        category = TaskCategory.SOURCE,
        intent = "List files by size",
        intentDetail = "sorts source files by line count",
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(OVER to "100")),
            UsageExample(listOf(OVER to "200", TOP to "10")),
        ),
    )

    val DUPLICATES = TaskDef(
        goal = "duplicates",
        description = "Detect duplicate code blocks across source files",
        params = FORMAT_PARAMS + listOf(TOP, MIN_TOKENS, SCOPE),
        requiresCompilation = false,
        category = TaskCategory.SOURCE,
        intent = "Find duplicate code",
        intentDetail = "token-based duplicate detection across source files",
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(SCOPE to "prod")),
            UsageExample(listOf(MIN_TOKENS to "100")),
            UsageExample(listOf(MIN_TOKENS to "30", TOP to "20")),
        ),
    )

    val RENAME_PARAM_TASK = TaskDef(
        goal = "rename-param",
        description = "Rename a method parameter and update all named-argument call sites",
        params = FORMAT_PARAMS + listOf(RENAME_CLASS, RENAME_METHOD, RENAME_PARAM, RENAME_NEW_NAME, PREVIEW),
        requiresCompilation = false,
        category = TaskCategory.SOURCE,
        intent = "Rename a parameter",
        intentDetail = "updates named-argument call sites",
        examples = listOf(
            UsageExample(listOf(RENAME_CLASS to "com.example.UserService", RENAME_METHOD to "findUsers", RENAME_PARAM to "limit", RENAME_NEW_NAME to "maxResults")),
            UsageExample(listOf(RENAME_CLASS to "com.example.UserService", RENAME_METHOD to "findUsers", RENAME_PARAM to "limit", RENAME_NEW_NAME to "maxResults", PREVIEW to null)),
        ),
    )

    val RENAME_METHOD_TASK = TaskDef(
        goal = "rename-method",
        description = "Rename a method and update all call sites (including interface implementations)",
        params = FORMAT_PARAMS + listOf(RENAME_CLASS, RENAME_METHOD, RENAME_NEW_NAME, PREVIEW),
        requiresCompilation = false,
        category = TaskCategory.SOURCE,
        intent = "Rename a method",
        intentDetail = "updates all call sites incl. interface impls",
        examples = listOf(
            UsageExample(listOf(RENAME_CLASS to "com.example.UserService", RENAME_METHOD to "findUsers", RENAME_NEW_NAME to "searchUsers")),
            UsageExample(listOf(RENAME_CLASS to "com.example.UserService", RENAME_METHOD to "findUsers", RENAME_NEW_NAME to "searchUsers", PREVIEW to null)),
        ),
    )

    val MOVE_CLASS_TASK = TaskDef(
        goal = "move-class",
        description = "Move and/or rename a class (or a whole file with --from-file), updating all references",
        params = FORMAT_PARAMS + listOf(MOVE_FROM, MOVE_TO, FROM_FILE, PREVIEW),
        requiresCompilation = true,
        category = TaskCategory.SOURCE,
        intent = "Move/rename a class",
        intentDetail = "rewrites package decl, all imports, same-package refs",
        aliases = listOf("rename-class"),
        examples = listOf(
            UsageExample(listOf(MOVE_FROM to "com.example.services.UserService", MOVE_TO to "com.example.domain.UserService")),
            UsageExample(listOf(MOVE_FROM to "com.example.services.UserService", MOVE_TO to "com.example.services.AccountService")),
            UsageExample(listOf(MOVE_FROM to "com.example.services.UserService", MOVE_TO to "com.example.domain.AccountService", PREVIEW to null)),
            UsageExample(listOf(FROM_FILE to "src/main/kotlin/com/example/services/Events.kt", MOVE_TO to "com.example.events")),
        ),
    )

    val MOVE_PACKAGE_TASK = TaskDef(
        goal = "move-package",
        description = "Move all classes in a package to a different package, updating all references",
        params = FORMAT_PARAMS + listOf(FROM_PACKAGE, TO_PACKAGE, PREVIEW),
        requiresCompilation = true,
        category = TaskCategory.SOURCE,
        intent = "Batch-move every class in a package to a new package",
        intentDetail = "iterates move-class for each class found in the source package",
        examples = listOf(
            UsageExample(listOf(FROM_PACKAGE to "com.example.services", TO_PACKAGE to "com.example.domain")),
            UsageExample(listOf(FROM_PACKAGE to "com.example.services", TO_PACKAGE to "com.example.domain", PREVIEW to null)),
        ),
    )

    val MOVE_FILE_TASK = TaskDef(
        goal = "move-file",
        description = "Move a Kotlin source file to a new package, updating all class and top-level declaration references",
        params = FORMAT_PARAMS + listOf(FROM_FILE, TO_PACKAGE, PREVIEW),
        requiresCompilation = true,
        category = TaskCategory.SOURCE,
        intent = "Move a file to another package",
        intentDetail = "updates package declaration + all imports + references",
        examples = listOf(
            UsageExample(listOf(FROM_FILE to "src/main/kotlin/com/example/Metrics.kt", TO_PACKAGE to "com.example.billing")),
            UsageExample(listOf(FROM_FILE to "src/main/kotlin/com/example/Metrics.kt", TO_PACKAGE to "com.example.billing", PREVIEW to null)),
        ),
    )

    val RENAME_PROPERTY_TASK = TaskDef(
        goal = "rename-property",
        description = "Rename a property (val/var) and update all access sites, constructor call sites, and copy() calls",
        params = FORMAT_PARAMS + listOf(RENAME_CLASS, RENAME_PROPERTY, RENAME_NEW_NAME, PREVIEW),
        requiresCompilation = false,
        category = TaskCategory.SOURCE,
        intent = "Rename a property",
        intentDetail = "updates access sites, constructors, copy() calls",
        examples = listOf(
            UsageExample(listOf(RENAME_CLASS to "com.example.UserProfile", RENAME_PROPERTY to "fullName", RENAME_NEW_NAME to "displayName")),
            UsageExample(listOf(RENAME_CLASS to "com.example.UserProfile", RENAME_PROPERTY to "fullName", RENAME_NEW_NAME to "displayName", PREVIEW to null)),
        ),
    )

    val CHANGE_SIGNATURE_TASK = TaskDef(
        goal = "change-signature",
        description = "Change method signature: add, remove, or reorder parameters. Rewrites declaration and all call sites.",
        params = FORMAT_PARAMS + listOf(RENAME_CLASS, RENAME_METHOD, CHANGE_SIG_PARAMS, CHANGE_SIG_DEFAULTS, PREVIEW),
        requiresCompilation = true,
        category = TaskCategory.SOURCE,
        intent = "Change method parameters",
        intentDetail = "add/remove/reorder params, updates call sites",
        examples = listOf(
            UsageExample(listOf(RENAME_CLASS to "com.example.UserService", RENAME_METHOD to "findUsers", CHANGE_SIG_PARAMS to "\"limit: Int, offset: Int, query: String\"", CHANGE_SIG_DEFAULTS to "\"query=\\\"\\\"\"")),
            UsageExample(listOf(RENAME_CLASS to "com.example.UserService", RENAME_METHOD to "findUsers", CHANGE_SIG_PARAMS to "\"offset: Int, limit: Int\"", PREVIEW to null)),
        ),
    )

    val SAFE_DELETE_TASK = TaskDef(
        goal = "safe-delete",
        description = "Delete a class or method only if it has no usages (verified via bytecode analysis)",
        params = FORMAT_PARAMS + listOf(RENAME_CLASS, RENAME_METHOD, PREVIEW),
        requiresCompilation = true,
        category = TaskCategory.SOURCE,
        intent = "Safely delete unused code",
        intentDetail = "verifies zero usages via bytecode before deleting",
        examples = listOf(
            UsageExample(listOf(RENAME_CLASS to "com.example.UnusedService")),
            UsageExample(listOf(RENAME_CLASS to "com.example.UserService", RENAME_METHOD to "unusedMethod")),
            UsageExample(listOf(RENAME_CLASS to "com.example.UnusedService", PREVIEW to null)),
        ),
    )

    val EXECUTE_PLAN_TASK = TaskDef(
        goal = "execute-plan",
        description = "Execute a refactoring plan file, applying each move sequentially",
        params = FORMAT_PARAMS + listOf(PLAN_FILE, PREVIEW),
        requiresCompilation = true,
        category = TaskCategory.SOURCE,
        intent = "Execute a sequence of refactoring steps from a plan file",
        intentDetail = "reads plan JSON, applies each move-class step in order",
        examples = listOf(
            UsageExample(listOf(PLAN_FILE to "refactoring-plan.json")),
            UsageExample(listOf(PLAN_FILE to "refactoring-plan.json", PREVIEW to null)),
        ),
    )

    val TEST_COUPLING = TaskDef(
        goal = "test-coupling",
        description = "Detect tests that bypass domain services by calling port interface methods directly (TTTD violations)",
        params = FORMAT_PARAMS + listOf(PORTS, DETAIL, EXCLUDE) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(PORTS to "\".*Repository|.*Client\"")),
            UsageExample(listOf(PORTS to "\".*Repository|.*Client|.*Gateway\"", DETAIL to "true")),
        ),
    )

    val REPORT = TaskDef(
        goal = "report",
        description = "Consolidated codebase health report: metrics, cycles, rings, move-suggest, cohesion, dead code",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, TOP, AFTER, NO_FOLLOW, EXCLUDE_ANNOTATED, TREAT_AS_DEAD) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        requiresTestCompilation = true,
        category = TaskCategory.COMPOSITE,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(SCOPE to "prod")),
        ),
    )

    val CONVERGE = TaskDef(
        goal = "converge",
        description = "Composite architectural signal: intersect mode cross-references cycles/rings/change-coupling into a ranked ACT NOW/LATENT/MISSING ABSTRACTION list; risk mode ranks classes by change-frequency x complexity x coupling. Includes test sources by default (--scope=all); when the result is large it emits an advisory pointing at --scope=prod / --exclude, since manually-wired DI and shared test infrastructure often inflate it with cycles that don't exist in production.",
        params = FORMAT_PARAMS + listOf(CONVERGE_MODE, PACKAGE_FILTER, EXCLUDE, AFTER, MIN_SHARED_REVS, MIN_COUPLING, MAX_CHANGESET_SIZE, NO_FOLLOW, TOP) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.COMPOSITE,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(CONVERGE_MODE to "risk")),
            UsageExample(listOf(SCOPE to "prod")),
            UsageExample(listOf(EXCLUDE to "\"\\\\.di\\\\.|testutil|e2e\"")),
        ),
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
        WHY_DEPENDS,
        DSM,
        CYCLE_DETECTION,
        SIMULATE_MOVE,
        FIND_USAGES,
        RANK,
        DEAD,
        FIND_STRING_CONSTANT,
        ANNOTATIONS,
        COMPLEXITY,
        CLASS_METRICS,
        METRICS,
        HOTSPOTS,
        CHURN,
        CODE_AGE,
        AUTHORS,
        COUPLING,
        CHANGED_SINCE,
        CONTEXT,
        DISTANCE,
        STRENGTH,
        COHESION,
        MOVE_SUGGEST,
        SUGGEST_STRUCTURE,
        VOLATILITY,
        BALANCE,
        RINGS,
        TYPE_AFFINITY,
        REPORT,
        CONVERGE,
        SIZE,
        DUPLICATES,
        TEST_COUPLING,
        RENAME_PARAM_TASK,
        RENAME_METHOD_TASK,
        MOVE_CLASS_TASK,
        MOVE_FILE_TASK,
        MOVE_PACKAGE_TASK,
        RENAME_PROPERTY_TASK,
        CHANGE_SIGNATURE_TASK,
        SAFE_DELETE_TASK,
        EXECUTE_PLAN_TASK,
        HELP,
        AGENT_HELP,
        CONFIG_HELP,
    )

    /** All SOURCE-category tasks that have refactoring intent metadata. */
    val REFACTORING_TASKS: List<TaskDef> = ALL_TASKS.filter {
        it.category == TaskCategory.SOURCE && it.intent != null
    }
}
