package no.f12.codenavigator

import no.f12.codenavigator.analysis.AuthorAnalysisTask
import no.f12.codenavigator.analysis.ChangeCouplingTask
import no.f12.codenavigator.analysis.ChurnTask
import no.f12.codenavigator.analysis.CodeAgeTask
import no.f12.codenavigator.analysis.HotspotTask
import no.f12.codenavigator.navigation.FindCalleesTask
import no.f12.codenavigator.navigation.FindCallersTask
import no.f12.codenavigator.navigation.FindClassDetailTask
import no.f12.codenavigator.navigation.FindClassTask
import no.f12.codenavigator.navigation.FindInterfaceImplsTask
import no.f12.codenavigator.navigation.FindSymbolTask
import no.f12.codenavigator.navigation.ListClassesTask
import no.f12.codenavigator.navigation.PackageDepsTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodeNavigatorPlugin : Plugin<Project> {
    override fun apply(project: Project) {

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

        project.tasks.register("cnavHelp", CodeNavigatorHelpTask::class.java) {
            description = "Shows available code-navigator tasks and their usage"
            group = "code-navigator"
        }

        project.tasks.register("cnavAgentHelp", AgentHelpTask::class.java) {
            description = "Shows AI agent instructions for using code-navigator effectively"
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
