package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindInterfaceImplsTask : DefaultTask() {

    @TaskAction
    fun findImplementors() {
        val pattern = project.findProperty("pattern")?.toString()
            ?: throw GradleException("Missing required property 'pattern'. Usage: ./gradlew cnavInterfaces -Ppattern=<regex>")
        val includeTest = project.findProperty("includetest")?.toString()?.toBoolean() ?: false

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val classDirectories = mutableListOf<File>()
        classDirectories.addAll(sourceSets.getByName("main").output.classesDirs.files)

        val cacheFileName = if (includeTest) {
            sourceSets.findByName("test")?.let { testSourceSet ->
                classDirectories.addAll(testSourceSet.output.classesDirs.files)
            }
            "interface-registry-all.cache"
        } else {
            "interface-registry.cache"
        }

        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "cnav/$cacheFileName")
        val registry = InterfaceRegistryCache.getOrBuild(cacheFile, classDirectories)
        val matchingInterfaces = registry.findInterfaces(pattern)

        if (matchingInterfaces.isEmpty()) {
            logger.lifecycle("No interfaces found matching '$pattern'")
            return
        }

        val output = InterfaceFormatter.format(registry, matchingInterfaces)
        logger.lifecycle(output)
    }
}
