package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.core.JarClassScanner
import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import no.f12.codenavigator.navigation.symbol.FindSymbolConfig
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.symbol.SymbolExtractor
import no.f12.codenavigator.navigation.symbol.SymbolFilter
import no.f12.codenavigator.navigation.symbol.SymbolIndexCache
import no.f12.codenavigator.navigation.symbol.SymbolTableFormatter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-symbol", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.COMPILE)
class FindSymbolMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "pattern", required = true)
    private var pattern: String? = null

    @Parameter(property = "include-test")
    private var includeTest: String? = null

    @Parameter(property = "jar")
    private var jar: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val config = try {
            FindSymbolConfig.parse(TaskRegistry.FIND_SYMBOL.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(e.message)
        }

        val allSymbols = if (config.jar != null) {
            val jarFile = project.resolveJar(config.jar!!)
            val entries = JarClassScanner.scan(jarFile)
            entries.flatMap { entry ->
                try {
                    val info = ClassInfoExtractor.extract(entry.bytes)
                    if (info.isUserDefinedClass) {
                        SymbolExtractor.extract(entry.bytes)
                    } else {
                        emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }.sortedWith(compareBy({ it.packageName.toString() }, { it.className }, { it.symbolName }))
        } else {
            val taggedDirs = project.taggedClassDirectories()
            val resolver = SourceSetResolver.from(taggedDirs)

            val cacheFile = File(project.build.directory, "cnav/symbol-index-all.cache")
            val result = SymbolIndexCache.getOrBuild(cacheFile, resolver.classDirectories)
            val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
            SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
            val filtered = result.data.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }
            filtered
        }

        val matches = SymbolFilter.filter(allSymbols, config.pattern)

        if (matches.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No symbols matching '${config.pattern}' found."))
            return
        }
        println(OutputWrapper.formatAndWrap(config.format,
            text = { SymbolTableFormatter.format(matches) },
            json = { JsonFormatter.formatSymbols(matches) },
            llm = { LlmFormatter.formatSymbols(matches) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        jar?.let { put("jar", it) }
        includeTest?.let { put("include-test", it) }
        scope?.let { put("scope", it) }
    }
}
