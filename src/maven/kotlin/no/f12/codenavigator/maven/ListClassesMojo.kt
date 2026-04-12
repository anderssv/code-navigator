package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.formatting.TableFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.core.JarClassScanner
import no.f12.codenavigator.navigation.classinfo.ClassFilter
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import no.f12.codenavigator.navigation.classinfo.ListClassesConfig
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "list-classes", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.COMPILE)
class ListClassesMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "pattern")
    private var pattern: String? = null

    @Parameter(property = "jar")
    private var jar: String? = null

    @Parameter(property = "prod-only")
    private var prodOnly: String? = null

    @Parameter(property = "test-only")
    private var testOnly: String? = null

    override fun execute() {
        val config = ListClassesConfig.parse(TaskRegistry.LIST_CLASSES.enhanceProperties(buildPropertyMap()))

        val classes = if (config.jar != null) {
            val jarFile = project.resolveJar(config.jar!!)
            val entries = JarClassScanner.scan(jarFile)
            entries.mapNotNull { entry ->
                try {
                    val info = ClassInfoExtractor.extract(entry.bytes)
                    if (info.isUserDefinedClass) info else null
                } catch (_: Exception) {
                    null
                }
            }.sortedBy { it.className }
        } else {
            val taggedDirs = project.taggedClassDirectories()
            val resolver = SourceSetResolver.from(taggedDirs)

            if (resolver.classDirectories.isEmpty() || resolver.classDirectories.none { it.exists() }) {
                log.warn("Classes directory does not exist — run 'mvn compile' first.")
                return
            }

            val result = ClassIndexCache.getOrBuild(File(project.build.directory, "cnav/class-index-all.cache"), resolver.classDirectories)
            val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
            SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
            when {
                config.prodOnly -> result.data.filter { resolver.sourceSetOf(it.className) == SourceSet.MAIN }
                config.testOnly -> result.data.filter { resolver.sourceSetOf(it.className) == SourceSet.TEST }
                else -> result.data
            }
        }

        val filtered = if (config.pattern != null) {
            ClassFilter.filter(classes, config.pattern!!)
        } else {
            classes
        }

        if (filtered.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No classes found."))
            return
        }
        println(OutputWrapper.formatAndWrap(config.format,
            text = { TableFormatter.format(filtered) },
            json = { JsonFormatter.formatClasses(filtered) },
            llm = { LlmFormatter.formatClasses(filtered) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        jar?.let { put("jar", it) }
        prodOnly?.let { put("prod-only", it) }
        testOnly?.let { put("test-only", it) }
    }
}
