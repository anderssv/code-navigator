package no.f12.codenavigator.gradle

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.InterfaceFormatter
import no.f12.codenavigator.navigation.InterfaceRegistryCache
import no.f12.codenavigator.navigation.SkippedFileReporter

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
        val format = project.outputFormat()

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
        val result = InterfaceRegistryCache.getOrBuild(cacheFile, classDirectories)
        val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
        val registry = result.data
        val matchingInterfaces = registry.findInterfaces(pattern)

        if (matchingInterfaces.isEmpty()) {
            logger.lifecycle("No interfaces found matching '$pattern'")
            return
        }

        val output = when (format) {
            OutputFormat.JSON -> JsonFormatter.formatInterfaces(registry, matchingInterfaces)
            OutputFormat.LLM -> LlmFormatter.formatInterfaces(registry, matchingInterfaces)
            OutputFormat.TEXT -> InterfaceFormatter.format(registry, matchingInterfaces)
        }
        logger.lifecycle(OutputWrapper.wrap(output, format))
    }
}
