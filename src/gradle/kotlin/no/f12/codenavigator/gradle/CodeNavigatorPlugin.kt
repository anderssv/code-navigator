package no.f12.codenavigator.gradle

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

        project.tasks.register("cnavCallers", FindCallersTask::class.java) {
            description = "Shows who calls a given method as an indented tree. Usage: -Pmethod=<regex> -Pmaxdepth=N -Pprojectonly=true|false"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavCallees", FindCalleesTask::class.java) {
            description = "Shows what a method calls as an indented tree. Usage: -Pmethod=<regex> -Pmaxdepth=N -Pprojectonly=true|false"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavClass", FindClassDetailTask::class.java) {
            description = "Shows class signature (fields, methods, interfaces, superclass). Usage: -Ppattern=<regex>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavInterfaces", FindInterfaceImplsTask::class.java) {
            description = "Finds implementations of an interface. Usage: -Ppattern=<regex>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavDeps", PackageDepsTask::class.java) {
            description = "Shows package-level dependencies. Usage: [-Ppackage=<regex>]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavDsm", DsmTask::class.java) {
            description = "Shows Dependency Structure Matrix. Usage: [-Proot-package=<pkg>] [-Pdsm-depth=N] [-Pdsm-html=<path>]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavUsages", FindUsagesTask::class.java) {
            description = "Finds project references to external types/methods. Usage: -PownerClass=<class> [-Pmethod=<name>] or -Ptype=<class>"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavRank", RankTask::class.java) {
            description = "Ranks types by structural importance using PageRank on the call graph. Usage: [-Ptop=N] [-Pprojectonly=true|false]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavDead", DeadCodeTask::class.java) {
            description = "Finds potential dead code — classes and methods never referenced by other project code. Usage: [-Pfilter=<regex>] [-Pexclude=<regex>]"
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

        project.tasks.register("cnavHelpConfig", ConfigHelpTask::class.java) {
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

        project.tasks.register("cnavAge", CodeAgeTask::class.java) {
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

        // --- Startup indicator for all cnav tasks ---

        project.tasks.matching { it.group == "code-navigator" }.configureEach {
            doFirst { logger.lifecycle("\uD83E\uDDED code-navigator: $name") }
        }
    }
}
