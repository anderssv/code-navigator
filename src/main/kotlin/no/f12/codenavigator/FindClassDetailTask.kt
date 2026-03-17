package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindClassDetailTask : DefaultTask() {

    @TaskAction
    fun findClassDetail() {
        val pattern = project.findProperty("pattern")?.toString()
            ?: throw GradleException("Missing required property 'pattern'. Usage: ./gradlew cnavClass -Ppattern=<regex>")

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val matchingDetails = scanAndFilter(classDirectories, regex)

        if (matchingDetails.isEmpty()) {
            logger.lifecycle("No classes found matching '$pattern'")
            return
        }

        val output = ClassDetailFormatter.format(matchingDetails)
        logger.lifecycle(output)
    }

    private fun scanAndFilter(classDirectories: List<File>, regex: Regex): List<ClassDetail> =
        classDirectories
            .filter { it.exists() }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .filter { file ->
                        val info = ClassInfoExtractor.extract(file)
                        info.isUserDefinedClass && regex.containsMatchIn(info.className)
                    }
                    .map { ClassDetailExtractor.extract(it) }
                    .toList()
            }
            .sortedBy { it.className }
}
