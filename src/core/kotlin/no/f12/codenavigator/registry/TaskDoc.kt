package no.f12.codenavigator.registry

/**
 * What a task does to the project. Orthogonal to [TaskCategory], which says what it reads
 * (bytecode, git history, source files). Only [MUTATION] tasks get the refactoring treatment
 * in help output (preview protocol, "I want to..." lookup); a read-only source scanner such as
 * find-declaration is a [QUERY] even though its category is SOURCE.
 */
enum class TaskKind { QUERY, MUTATION }

/**
 * Per-task documentation, kept as data so every help surface (human help, agent guide,
 * per-task view, JSON schema list, interpretation heuristics, skill file) is a projection of the
 * same source and cannot drift from it. Lives next to [TaskDef] rather than inside it so the
 * registry stays a parameter table and the prose stays prose.
 *
 * All strings are [DocTemplate]s: `{t:goal}`, `{u:goal}`, `{p:name=value}`, `{pf:name}` and
 * `{cmd}` render per build tool.
 *
 * @param details      Longer explanation of what the task does and how; shown in human help and
 *                     the per-task view. One entry per paragraph line.
 * @param whenToUse    Questions an agent would be asking when this is the right task
 *                     ("Who uses type X?"). Drives the question index and the task directory.
 * @param interpretation Heuristics for reading the results. Drives the interpretation section.
 * @param schema       Example JSON output, one entry per line. Drives the schema section.
 * @param caveats      Things that surprise people: what is filtered, what it cannot see, when to
 *                     reach for something else.
 * @param related      Goals of tasks that answer neighbouring questions.
 */
data class TaskDoc(
    val details: List<String> = emptyList(),
    val whenToUse: List<String> = emptyList(),
    val interpretation: List<String> = emptyList(),
    val schema: List<String> = emptyList(),
    val caveats: List<String> = emptyList(),
    val related: List<String> = emptyList(),
) {
    companion object {
        val NONE = TaskDoc()
    }
}

/**
 * Minimal placeholder language for build-tool-neutral prose:
 *
 * - `{cmd}`            → `./gradlew` / `mvn`
 * - `{t:find-usages}`  → `cnavFindUsages` / `cnav:find-usages`
 * - `{u:find-usages}`  → `./gradlew cnavFindUsages` / `mvn cnav:find-usages`
 * - `{p:scope=prod}`   → `--scope=prod` / `-Dscope=prod` (the value runs to the closing brace)
 * - `{pf:preview}`     → `--preview` / `-Dpreview`
 */
object DocTemplate {
    private val PLACEHOLDER = Regex("""\{(cmd|t|u|p|pf)(?::([^}]*))?}""")

    fun render(template: String, tool: BuildTool): String =
        PLACEHOLDER.replace(template) { m ->
            val arg = m.groupValues[2]
            when (m.groupValues[1]) {
                "cmd" -> tool.command
                "t" -> tool.taskName(arg)
                "u" -> tool.usage(arg)
                "p" -> {
                    val eq = arg.indexOf('=')
                    require(eq > 0) { "Bad {p:} placeholder, expected name=value: ${m.value}" }
                    tool.param(arg.substring(0, eq), arg.substring(eq + 1))
                }
                "pf" -> tool.paramFlag(arg)
                else -> m.value
            }
        }

    fun render(lines: List<String>, tool: BuildTool): List<String> = lines.map { render(it, tool) }

    /** Every goal referenced via `{t:}`/`{u:}` in [template], for the dangling-reference test. */
    fun referencedGoals(template: String): Set<String> =
        PLACEHOLDER.findAll(template)
            .filter { it.groupValues[1] == "t" || it.groupValues[1] == "u" }
            .map { it.groupValues[2] }
            .toSet()
}
