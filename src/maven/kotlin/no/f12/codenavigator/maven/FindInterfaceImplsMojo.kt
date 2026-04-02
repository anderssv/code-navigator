package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.SourceSet
import no.f12.codenavigator.navigation.SourceSetResolver
import no.f12.codenavigator.navigation.interfaces.FindInterfaceImplsConfig
import no.f12.codenavigator.navigation.interfaces.InterfaceFormatter
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-interfaces")
@Execute(phase = LifecyclePhase.COMPILE)
class FindInterfaceImplsMojo : AbstractMojo() {

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

    @Parameter(property = "prod-only")
    private var prodOnly: String? = null

    @Parameter(property = "test-only")
    private var testOnly: String? = null

    override fun execute() {
        val config = try {
            FindInterfaceImplsConfig.parse(TaskRegistry.FIND_INTERFACES.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(e.message)
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        if (resolver.classDirectories.isEmpty() || resolver.classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val cacheFile = File(project.build.directory, "cnav/interface-registry-all.cache")
        val result = InterfaceRegistryCache.getOrBuild(cacheFile, resolver.classDirectories)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }

        val registry = when {
            config.prodOnly -> result.data.filteredByImplementor { resolver.sourceSetOf(it) == SourceSet.MAIN }
            config.testOnly -> result.data.filteredByImplementor { resolver.sourceSetOf(it) == SourceSet.TEST }
            else -> result.data
        }
        val matchingInterfaces = registry.findInterfaces(config.pattern)

        if (matchingInterfaces.isEmpty()) {
            println("No interfaces found matching '${config.pattern}'")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { InterfaceFormatter.format(registry, matchingInterfaces) },
            json = { JsonFormatter.formatInterfaces(registry, matchingInterfaces) },
            llm = { LlmFormatter.formatInterfaces(registry, matchingInterfaces) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        includeTest?.let { put("include-test", it) }
        prodOnly?.let { put("prod-only", it) }
        testOnly?.let { put("test-only", it) }
    }
}
