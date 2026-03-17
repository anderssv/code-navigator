package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindInterfaceImplsTask : DefaultTask() {

    @TaskAction
    fun findImplementors() {
        val pattern = project.findProperty("pattern")?.toString()
            ?: throw GradleException("Missing required property 'pattern'. Usage: ./gradlew cnavInterfaces -Ppattern=<regex>")

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val registry = InterfaceRegistry.build(classDirectories)
        val matchingInterfaces = registry.findInterfaces(pattern)

        if (matchingInterfaces.isEmpty()) {
            logger.lifecycle("No interfaces found matching '$pattern'")
            return
        }

        val output = InterfaceFormatter.format(registry, matchingInterfaces)
        logger.lifecycle(output)
    }
}
