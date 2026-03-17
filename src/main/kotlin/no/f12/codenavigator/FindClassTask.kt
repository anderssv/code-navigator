package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindClassTask : DefaultTask() {

    @TaskAction
    fun findClass() {
        val pattern = project.findProperty("pattern")?.toString()
            ?: throw GradleException("Missing required property 'pattern'. Usage: ./gradlew cnavFindClass -Ppattern=<regex>")

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()
        val cacheFile = File(project.layout.buildDirectory.asFile.get(), "code-navigator/class-index.txt")

        val allClasses = loadClasses(cacheFile, classDirectories)
        val matches = ClassFilter.filter(allClasses, pattern)
        val output = TableFormatter.format(matches)

        logger.lifecycle(output)
    }
}
