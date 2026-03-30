package no.f12.codenavigator.gradle

import no.f12.codenavigator.TaskRegistry
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodeNavigatorPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        project.extensions.create("codeNavigator", CodeNavigatorExtension::class.java)

        // --- Navigation tasks (bytecode-based, require compilation) ---

        project.tasks.register("cnavListClasses", ListClassesTask::class.java) {
            description = "Lists all classes in the project and their source files"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavFindClass", FindClassTask::class.java) {
            description = "Searches for classes matching a regex pattern. Usage: -Ppattern=<regex>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavFindSymbol", FindSymbolTask::class.java) {
            description = "Searches for symbols (methods/fields) matching a regex pattern. Usage: -Ppattern=<regex>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavFindCallers", FindCallersTask::class.java) {
            description = "Shows who calls a given method as an indented tree. Usage: -Ppattern=<regex> -Pmaxdepth=N -Pproject-only=true|false"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavFindCallees", FindCalleesTask::class.java) {
            description = "Shows what a method calls as an indented tree. Usage: -Ppattern=<regex> -Pmaxdepth=N -Pproject-only=true|false"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavClassDetail", FindClassDetailTask::class.java) {
            description = "Shows class signature (fields, methods, interfaces, superclass). Usage: -Ppattern=<regex>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavFindInterfaces", FindInterfaceImplsTask::class.java) {
            description = "Finds implementations of an interface. Usage: -Ppattern=<regex>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavTypeHierarchy", TypeHierarchyTask::class.java) {
            description = "Shows type hierarchy (supertypes upward, implementors downward). Usage: -Ppattern=<regex>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavPackageDeps", PackageDepsTask::class.java) {
            description = "Shows package-level dependencies. Usage: [-Ppackage=<regex>]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavDsm", DsmTask::class.java) {
            description = "Shows Dependency Structure Matrix. Usage: [-Ppackage-filter=<pkg>] [-Pdsm-depth=N] [-Pdsm-html=<path>]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavCycles", CyclesTask::class.java) {
            description = "Detects dependency cycles using Tarjan's SCC algorithm. Usage: [-Ppackage-filter=<pkg>] [-Pdsm-depth=N]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavFindUsages", FindUsagesTask::class.java) {
            description = "Finds project references to external types/methods. Usage: -Powner-class=<class> [-Pmethod=<name>] or -Ptype=<class>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavRank", RankTask::class.java) {
            description = "Ranks types by structural importance using PageRank on the call graph. Usage: [-Ptop=N] [-Pproject-only=true|false]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavDead", DeadCodeTask::class.java) {
            description = "Finds potential dead code — classes and methods never referenced by other project code. Usage: [-Pfilter=<regex>] [-Pexclude=<regex>]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavFindStringConstant", StringConstantTask::class.java) {
            description = "Searches string constants in bytecode matching a regex. Usage: -Ppattern=<regex>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavAnnotations", AnnotationsTask::class.java) {
            description = "Finds classes and methods by annotation pattern. Usage: -Ppattern=<regex> [-Pmethods=true]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavComplexity", ComplexityTask::class.java) {
            description = "Shows fan-in/fan-out complexity per class. Usage: -Ppattern=<pattern> [-Pproject-only=true] [-Pdetail=true]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavMetrics", MetricsTask::class.java) {
            description = "Quick project health snapshot: classes, packages, fan-in/out, cycles, dead code, hotspots"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavContext", ContextTask::class.java) {
            description = "Gather full context for a class: detail, callers, callees, interfaces. Usage: -Ppattern=<regex>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavHelp", CodeNavigatorHelpTask::class.java) {
            description = "Shows available code-navigator tasks and their usage"
            group = "code-navigator"
        }

        project.tasks.register("cnavAgentHelp", AgentHelpTask::class.java) {
            description = "Shows AI agent instructions for using code-navigator effectively"
            group = "code-navigator"
        }

        project.tasks.register("cnavConfigHelp", ConfigHelpTask::class.java) {
            description = "Shows all available configuration parameters (-P properties)"
            group = "code-navigator"
        }

        // --- Analysis tasks (git history, no compilation needed) ---

        project.tasks.register("cnavHotspots", HotspotTask::class.java) {
            description = "Shows most frequently changed files (hotspots). Usage: [-Pafter=YYYY-MM-DD] [-Pmin-revs=N] [-Ptop=N]"
            group = "code-navigator"
        }

        project.tasks.register("cnavCoupling", ChangeCouplingTask::class.java) {
            description = "Shows files that change together (temporal coupling). Usage: [-Pafter=YYYY-MM-DD] [-Pmin-shared-revs=N] [-Pmin-coupling=N]"
            group = "code-navigator"
        }

        project.tasks.register("cnavCodeAge", CodeAgeTask::class.java) {
            description = "Shows code age per file (time since last change). Usage: [-Pafter=YYYY-MM-DD] [-Ptop=N]"
            group = "code-navigator"
        }

        project.tasks.register("cnavAuthors", AuthorAnalysisTask::class.java) {
            description = "Shows number of distinct authors per file. Usage: [-Pafter=YYYY-MM-DD] [-Pmin-revs=N] [-Ptop=N]"
            group = "code-navigator"
        }

        project.tasks.register("cnavChurn", ChurnTask::class.java) {
            description = "Shows lines added/deleted per file (code churn). Usage: [-Pafter=YYYY-MM-DD] [-Ptop=N]"
            group = "code-navigator"
        }

        // --- Hybrid tasks (git + compilation) ---

        project.tasks.register("cnavChangedSince", ChangedSinceTask::class.java) {
            description = "Shows blast radius of changes since a git ref. Usage: -Pref=<git-ref> [-Pproject-only=true]"
            group = "code-navigator"
            dependsOn("classes")
        }

        // --- Deprecated legacy aliases ---

        for (task in TaskRegistry.ALL_TASKS) {
            val legacy = task.legacyGradleTaskName ?: continue
            project.tasks.register(legacy) {
                dependsOn(task.gradleTaskName)
                group = "code-navigator (deprecated)"
                description = "DEPRECATED: Use ${task.gradleTaskName} instead"
                doFirst {
                    logger.warn("WARNING: '$legacy' is deprecated. Use '${task.gradleTaskName}' instead.")
                }
            }
        }

        // --- Startup indicator for all cnav tasks ---

        project.tasks.matching { it.group?.startsWith("code-navigator") == true }.configureEach {
            doFirst { logger.lifecycle("\uD83E\uDDED code-navigator: $name") }
        }
    }
}
