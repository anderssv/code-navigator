package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class PackageDepsTask : DefaultTask() {

    @TaskAction
    fun showDeps() {
        val pattern = project.findProperty("package")?.toString()
        val projectOnly = project.findProperty("projectonly")?.toString()?.toBoolean() ?: false
        val reverse = project.findProperty("reverse")?.toString()?.toBoolean() ?: false

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/call-graph.cache")
        val graph = CallGraphCache.getOrBuild(cacheFile, classDirectories)

        val filter: ((MethodRef) -> Boolean)? =
            if (projectOnly) graph.projectClassFilter() else null

        val deps = PackageDependencyBuilder.build(graph, filter)

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

        val output = PackageDependencyFormatter.format(deps, packages, reverse)
        logger.lifecycle(output)
    }
}
