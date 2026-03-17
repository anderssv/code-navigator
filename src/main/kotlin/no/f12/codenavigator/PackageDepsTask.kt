package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class PackageDepsTask : DefaultTask() {

    @TaskAction
    fun showDeps() {
        val pattern = project.findProperty("package")?.toString()

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val graph = CallGraphBuilder.build(classDirectories)
        val deps = PackageDependencyBuilder.build(graph)

        val packages = if (pattern != null) {
            val matches = deps.findPackages(pattern)
            if (matches.isEmpty()) {
                logger.lifecycle("No packages found matching '$pattern'")
                return
            }
            matches
        } else {
            deps.allPackages()
        }

        val output = PackageDependencyFormatter.format(deps, packages)
        logger.lifecycle(output)
    }
}
