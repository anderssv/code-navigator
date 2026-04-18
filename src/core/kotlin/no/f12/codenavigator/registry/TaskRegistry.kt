package no.f12.codenavigator.registry

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.PatternEnhancer
import no.f12.codenavigator.navigation.annotation.FrameworkPresets
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
                format = properties["format"],
                llm = properties["llm"]?.toBoolean(),
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
    val LLM = ParamDef("llm", "true", "Output in compact, token-efficient LLM format", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val PATTERN = ParamDef("pattern", "<regex>", "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
    val CALL_PATTERN = ParamDef("pattern", "<regex>", "Class.method name regex (camelCase-aware: MyService.doWork matches com.example.MyService.doWork)", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING)
    val LEGACY_METHOD = ParamDef("method", "<regex>", "Deprecated: use pattern instead", flag = false, defaultValue = null, enhancePattern = true, type = ParamType.STRING, deprecated = true, deprecatedMessage = "'method' is deprecated for find-callers/find-callees. Use 'pattern' instead (Gradle: -Ppattern=MyClass.myMethod, Maven: -Dpattern=MyClass.myMethod).")
    val METHOD = ParamDef("method", "<regex>", "Method name regex", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val MAXDEPTH = ParamDef("maxdepth", "<N>", "Max call tree depth", flag = false, defaultValue = "3", enhancePattern = false, type = ParamType.INT)
    val PROJECTONLY = ParamDef("project-only", "false", "Hide JDK/stdlib/library classes (default: on)", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)
    val FILTER_SYNTHETIC = ParamDef("filter-synthetic", "false", "Set false to include synthetic methods (equals, hashCode, copy, componentN, etc.)", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)
    val TOP = ParamDef("top", "<N>", "Max results", flag = false, defaultValue = "50", enhancePattern = false, type = ParamType.INT)
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
    val TREAT_AS_DEAD = ParamDef("treat-as-dead", "<name>", "Treat framework-annotated code as potentially dead (all frameworks protected by default). Available: ${FrameworkPresets.availablePresets().sorted().joinToString(", ")}. Use ALL to remove all framework protections.", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.LIST_STRING)
    val DETAIL = ParamDef("detail", "true", "Show individual call details", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.BOOLEAN)
    val COLLAPSE_LAMBDAS = ParamDef("collapse-lambdas", "false", "Set false to show lambda classes separately", flag = false, defaultValue = "true", enhancePattern = false, type = ParamType.BOOLEAN)
    val MIN_SHARED_REVS = ParamDef("min-shared-revs", "<N>", "Min shared commits", flag = false, defaultValue = "5", enhancePattern = false, type = ParamType.INT)
    val MIN_COUPLING = ParamDef("min-coupling", "<N>", "Min coupling degree %", flag = false, defaultValue = "30", enhancePattern = false, type = ParamType.INT)
    val MAX_CHANGESET_SIZE = ParamDef("max-changeset-size", "<N>", "Skip commits touching more files", flag = false, defaultValue = "30", enhancePattern = false, type = ParamType.INT)
    val METRICS_TOP = ParamDef("top", "<N>", "Max results per section", flag = false, defaultValue = "5", enhancePattern = false, type = ParamType.INT)
    val SECTION = ParamDef("section", "<name>", "Help section: install, workflow, interpretation, schemas, extraction", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val JAR = ParamDef("jar", "<path-or-artifact>", "Scan a JAR file instead of project classes. Value: file path or artifact coordinate (group:name)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val REF = ParamDef("ref", "<git-ref>", "Git ref to compare against (branch, tag, or commit SHA)", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val STRING_PATTERN = ParamDef("pattern", "<regex>", "Regex to match against string constant values", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val METHODS = ParamDef("methods", "true", "Also search method-level annotations", flag = false, defaultValue = "false", enhancePattern = false, type = ParamType.BOOLEAN)
    val CONTEXT_MAXDEPTH = ParamDef("maxdepth", "<N>", "Max call tree depth (default: 2)", flag = false, defaultValue = "2", enhancePattern = false, type = ParamType.INT)
    val LAYER_CONFIG = ParamDef("config", "<path>", "Path to layer config file", flag = false, defaultValue = ".cnav-layers.json", enhancePattern = false, type = ParamType.STRING)
    val INIT = ParamDef("init", "true", "Generate starter config file", flag = false, defaultValue = "false", enhancePattern = false, type = ParamType.BOOLEAN)
    val RENAME_CLASS = ParamDef("target-class", "<fqcn>", "Fully qualified class name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val RENAME_METHOD = ParamDef("method", "<name>", "Method name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val RENAME_PARAM = ParamDef("param", "<name>", "Current parameter name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val RENAME_PROPERTY = ParamDef("property", "<name>", "Current property name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val RENAME_NEW_NAME = ParamDef("new-name", "<name>", "New name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val PREVIEW = ParamDef("preview", "true", "Preview changes without writing to source files", flag = true, defaultValue = null, enhancePattern = false, type = ParamType.FLAG)
    val MIN_TOKENS = ParamDef("min-tokens", "<N>", "Minimum duplicate token sequence length", flag = false, defaultValue = "50", enhancePattern = false, type = ParamType.INT)
    val MOVE_FROM = ParamDef("from", "<fqcn>", "Fully qualified class name to move/rename", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)
    val MOVE_TO = ParamDef("to", "<fqcn>", "Target fully qualified class name", flag = false, defaultValue = null, enhancePattern = false, type = ParamType.STRING)

    val FORMAT_PARAMS = listOf(FORMAT, LLM)
    val SOURCE_SET_PARAMS = listOf(SCOPE)

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
        description = "Find callers of a method (call tree)",
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
        description = "Find methods called by a method (call tree)",
        params = FORMAT_PARAMS + listOf(CALL_PATTERN, LEGACY_METHOD, MAXDEPTH, PROJECTONLY, FILTER_SYNTHETIC, SCOPE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavCallees",
        examples = listOf(
            UsageExample(listOf(CALL_PATTERN to "resetPassword", MAXDEPTH to "3")),
            UsageExample(listOf(CALL_PATTERN to "\".*Controller\\\\.handle.*\"", MAXDEPTH to "5")),
            UsageExample(listOf(CALL_PATTERN to "\"Service\\\\.create\"", MAXDEPTH to "5", PROJECTONLY to "true")),
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

    val DSM = TaskDef(
        goal = "dsm",
        description = "Generate Dependency Structure Matrix",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, DSM_HTML, CYCLES, CYCLE, ROOT_PACKAGE) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(PACKAGE_FILTER to "com.example.api", DSM_DEPTH to "3")),
            UsageExample(listOf(INCLUDE_EXTERNAL to "true", PACKAGE_FILTER to "com.example")),
            UsageExample(listOf(DSM_HTML to "build/dsm.html")),
            UsageExample(listOf(CYCLES to "true")),
            UsageExample(listOf(CYCLE to "api,service")),
        ),
    )

    val CYCLE_DETECTION = TaskDef(
        goal = "cycles",
        description = "Detect dependency cycles using Tarjan's SCC algorithm",
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, ROOT_PACKAGE) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(PACKAGE_FILTER to "com.example", DSM_DEPTH to "3")),
        ),
    )

    val FIND_USAGES = TaskDef(
        goal = "find-usages",
        description = "Find project references to types, methods, and fields/properties",
        params = FORMAT_PARAMS + listOf(OWNER_CLASS, METHOD, FIELD, TYPE, OUTSIDE_PACKAGE, FILTER_SYNTHETIC, SCOPE),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        legacyGradleTaskName = "cnavUsages",
        examples = listOf(
            UsageExample(listOf(OWNER_CLASS to "kotlinx.datetime.LocalDate", METHOD to "getMonthNumber")),
            UsageExample(listOf(OWNER_CLASS to "kotlinx.datetime.Clock")),
            UsageExample(listOf(OWNER_CLASS to "com.example.Config", FIELD to "timeout")),
            UsageExample(listOf(TYPE to "kotlinx.datetime.Instant")),
            UsageExample(listOf(TYPE to "kotlinx.datetime.Instant", OUTSIDE_PACKAGE to "com.example.datetime")),
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
        params = FORMAT_PARAMS + listOf(FILTER, EXCLUDE, CLASSES_ONLY, EXCLUDE_ANNOTATED, SCOPE, TREAT_AS_DEAD),
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

    val METRICS = TaskDef(
        goal = "metrics",
        description = "Quick project health snapshot: classes, packages, fan-in/out, cycles, dead code, hotspots",
        params = FORMAT_PARAMS + listOf(AFTER, METRICS_TOP, NO_FOLLOW, PACKAGE_FILTER, INCLUDE_EXTERNAL, EXCLUDE_ANNOTATED, TREAT_AS_DEAD, ROOT_PACKAGE) + SOURCE_SET_PARAMS,
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
        params = listOf(SECTION),
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
        description = "Find classes and methods by annotation pattern",
        params = FORMAT_PARAMS + listOf(PATTERN, METHODS) + SOURCE_SET_PARAMS + listOf(INCLUDETEST),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(listOf(PATTERN to "RestController")),
            UsageExample(listOf(PATTERN to "\".*Mapping\"", METHODS to "true")),
            UsageExample(listOf(PATTERN to "Scheduled", METHODS to "true")),
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
        params = FORMAT_PARAMS + listOf(PACKAGE_FILTER, INCLUDE_EXTERNAL, DSM_DEPTH, TOP, AFTER, MIN_REVS, NO_FOLLOW) + SOURCE_SET_PARAMS,
        requiresCompilation = true,
        category = TaskCategory.COMPOSITE,
        paramDefaultOverrides = mapOf("top" to "all"),
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(PACKAGE_FILTER to "com.example")),
            UsageExample(listOf(TOP to "20")),
        ),
    )

    val LAYER_CHECK = TaskDef(
        goal = "layer-check",
        description = "Check architecture layer conformance against config",
        params = FORMAT_PARAMS + listOf(LAYER_CONFIG, INIT),
        requiresCompilation = true,
        category = TaskCategory.NAVIGATION,
        examples = listOf(
            UsageExample(emptyList()),
            UsageExample(listOf(INIT to "true")),
            UsageExample(listOf(LAYER_CONFIG to "custom-layers.json")),
        ),
    )

    val SIZE = TaskDef(
        goal = "size",
        description = "List source files by line count",
        params = FORMAT_PARAMS + listOf(TOP, OVER),
        requiresCompilation = false,
        category = TaskCategory.SOURCE,
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
        examples = listOf(
            UsageExample(listOf(RENAME_CLASS to "com.example.UserService", RENAME_METHOD to "findUsers", RENAME_NEW_NAME to "searchUsers")),
            UsageExample(listOf(RENAME_CLASS to "com.example.UserService", RENAME_METHOD to "findUsers", RENAME_NEW_NAME to "searchUsers", PREVIEW to null)),
        ),
    )

    val MOVE_CLASS_TASK = TaskDef(
        goal = "move-class",
        description = "Move and/or rename a class, updating all references",
        params = FORMAT_PARAMS + listOf(MOVE_FROM, MOVE_TO, PREVIEW),
        requiresCompilation = true,
        category = TaskCategory.SOURCE,
        aliases = listOf("rename-class"),
        examples = listOf(
            UsageExample(listOf(MOVE_FROM to "com.example.services.UserService", MOVE_TO to "com.example.domain.UserService")),
            UsageExample(listOf(MOVE_FROM to "com.example.services.UserService", MOVE_TO to "com.example.services.AccountService")),
            UsageExample(listOf(MOVE_FROM to "com.example.services.UserService", MOVE_TO to "com.example.domain.AccountService", PREVIEW to null)),
        ),
    )

    val RENAME_PROPERTY_TASK = TaskDef(
        goal = "rename-property",
        description = "Rename a property (val/var) and update all access sites, constructor call sites, and copy() calls",
        params = FORMAT_PARAMS + listOf(RENAME_CLASS, RENAME_PROPERTY, RENAME_NEW_NAME, PREVIEW),
        requiresCompilation = false,
        category = TaskCategory.SOURCE,
        examples = listOf(
            UsageExample(listOf(RENAME_CLASS to "com.example.UserProfile", RENAME_PROPERTY to "fullName", RENAME_NEW_NAME to "displayName")),
            UsageExample(listOf(RENAME_CLASS to "com.example.UserProfile", RENAME_PROPERTY to "fullName", RENAME_NEW_NAME to "displayName", PREVIEW to null)),
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
        DISTANCE,
        STRENGTH,
        VOLATILITY,
        BALANCE,
        LAYER_CHECK,
        SIZE,
        DUPLICATES,
        RENAME_PARAM_TASK,
        RENAME_METHOD_TASK,
        MOVE_CLASS_TASK,
        RENAME_PROPERTY_TASK,
        HELP,
        AGENT_HELP,
        CONFIG_HELP,
    )
}
