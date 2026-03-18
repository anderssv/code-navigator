package no.f12.codenavigator

import org.gradle.api.Plugin
import org.gradle.api.Project

class CodeNavigatorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
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
            description = "Shows who calls a given method as an indented tree. Usage: -Pmethod=<regex> [-Pdepth=N]"
            group = "code-navigator"
            dependsOn("classes")
        }

        project.tasks.register("cnavCallees", FindCalleesTask::class.java) {
            description = "Shows what a method calls as an indented tree. Usage: -Pmethod=<regex> [-Pdepth=N]"
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
    }
}
