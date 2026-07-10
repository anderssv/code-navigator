package no.f12.codenavigator.gradle

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.bytecode.JarClassScanner
import no.f12.codenavigator.navigation.classinfo.ClassDetailExtractor
import no.f12.codenavigator.navigation.classinfo.ClassDetailFormatter
import no.f12.codenavigator.navigation.classinfo.ClassDetailScanner
import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import no.f12.codenavigator.navigation.classinfo.FindClassDetailConfig
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter

import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Produces console output only")
abstract class FindClassDetailTask : CodeNavigatorTask() {

    @Option(option = "pattern", description = "Class/symbol name regex (camelCase-aware: MyService matches com.example.MyService)")
    @get:Internal
    var pattern: String? = null

    @Option(option = "jar", description = "Scan a JAR file instead of project classes. Value: file path or artifact coordinate (group:name)")
    @get:Internal
    var jar: String? = null

    @Option(option = "scope", description = "Filter by source set: all (default), prod (production only), test (test only)")
    @get:Internal
    var scope: String? = null

    override fun taskOptionsMap(): Map<String, String?> = buildMap {
        pattern?.let { put("pattern", it) }
        jar?.let { put("jar", it) }
        scope?.let { put("scope", it) }
    }

    @TaskAction
    fun findClassDetail() {
        val config = try {
            FindClassDetailConfig.parse(
                TaskRegistry.CLASS_DETAIL.enhanceProperties(buildOptionsMap()),
            )
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "${e.message}\n${TaskRegistry.CLASS_DETAIL.usageHint(BuildTool.GRADLE)}",
            )
        }

        val regex = Regex(config.pattern, RegexOption.IGNORE_CASE)

        val matchingDetails = if (config.jar != null) {
            val jarFile = project.resolveJar(config.jar)
            val entries = JarClassScanner.scan(jarFile)
            entries.mapNotNull { entry ->
                try {
                    val info = ClassInfoExtractor.extract(entry.bytes)
                    if (info.isUserDefinedClass && info.className.matches(regex)) {
                        ClassDetailExtractor.extract(entry.bytes)
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }.sortedBy { it.className }
        } else {
            val taggedDirs = project.taggedClassDirectories()
            val resolver = SourceSetResolver.from(taggedDirs)

            val result = ClassDetailScanner.scan(resolver.classDirectories, config.pattern)
            val reportFile = File(project.layout.buildDirectory.asFile.get(), "cnav/skipped-files.txt")
            SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { logger.warn(it) }
            result.data.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }
        }

        if (matchingDetails.isEmpty()) {
            logger.lifecycle(OutputWrapper.emptyResult(config.format, "No classes found matching '${config.pattern}'"))
            return
        }

        logger.lifecycle(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> ClassDetailFormatter.format(matchingDetails)
        OutputFormat.JSON -> ClassDetailFormatter.formatJson(matchingDetails)
        OutputFormat.LLM -> ClassDetailFormatter.formatLlm(matchingDetails)
    }
})
    }
}
