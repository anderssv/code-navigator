package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindCalleesTask : DefaultTask() {

    @TaskAction
    fun findCallees() {
        val methodPattern = project.findProperty("method")?.toString()
            ?: throw GradleException("Missing required property 'method'. Usage: ./gradlew cnavCallees -Pmethod=<regex>")
        val maxDepth = project.findProperty("depth")?.toString()?.toIntOrNull() ?: 3

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val graph = CallGraphBuilder.build(classDirectories)
        val methods = graph.findMethods(methodPattern)

        if (methods.isEmpty()) {
            logger.lifecycle("No methods found matching '$methodPattern'")
            return
        }

        val output = CalleeTreeFormatter.format(graph, methods, maxDepth)
        logger.lifecycle(output)
    }
}
