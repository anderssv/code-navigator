package no.f12.codenavigator

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindSymbolTask : DefaultTask() {

    @TaskAction
    fun findSymbol() {
        val pattern = project.findProperty("pattern")?.toString()
            ?: throw GradleException("Missing required property 'pattern'. Usage: ./gradlew cnavFindSymbol -Ppattern=<regex>")

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.getByName("main")
        val classDirectories = mainSourceSet.output.classesDirs.files.toList()

        val allSymbols = SymbolScanner.scan(classDirectories)
        val matches = SymbolFilter.filter(allSymbols, pattern)
        val output = SymbolTableFormatter.format(matches)

        logger.lifecycle(output)
    }
}
