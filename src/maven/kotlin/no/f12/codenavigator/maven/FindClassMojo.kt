package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.formatting.TableFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.core.JarClassScanner
import no.f12.codenavigator.navigation.classinfo.ClassFilter
import no.f12.codenavigator.navigation.classinfo.ClassIndexCache
import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import no.f12.codenavigator.navigation.classinfo.FindClassConfig
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-class", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.COMPILE)
class FindClassMojo : AbstractMojo() {

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

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val config = try {
            FindClassConfig.parse(TaskRegistry.FIND_CLASS.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "Missing required property 'pattern'. Usage: mvn cnav:find-class -Dpattern=<regex>",
            )
        }

        val allClasses = if (config.jar != null) {
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
            result.data.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }
        }

        val matches = ClassFilter.filter(allClasses, config.pattern)

        if (matches.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No classes matching '${config.pattern}' found."))
            return
        }
        println(OutputWrapper.formatAndWrap(config.format,
            text = { TableFormatter.format(matches) },
            json = { JsonFormatter.formatClasses(matches) },
            llm = { LlmFormatter.formatClasses(matches) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        jar?.let { put("jar", it) }
        scope?.let { put("scope", it) }
    }
}
