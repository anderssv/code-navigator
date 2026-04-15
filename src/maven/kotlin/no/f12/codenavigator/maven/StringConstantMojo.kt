package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.core.SourceSetResolver
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.stringconstant.StringConstantConfig
import no.f12.codenavigator.navigation.stringconstant.StringConstantFormatter
import no.f12.codenavigator.navigation.stringconstant.StringConstantScanner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-string-constant")
@Execute(phase = LifecyclePhase.COMPILE)
class StringConstantMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "pattern")
    private var pattern: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val config = StringConstantConfig.parse(
            TaskRegistry.FIND_STRING_CONSTANT.enhanceProperties(buildPropertyMap()),
        )

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        if (resolver.classDirectories.isEmpty() || resolver.classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val result = StringConstantScanner.scan(resolver.classDirectories, config.pattern)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val matches = result.data.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }

        if (matches.isEmpty()) {
            println("No string constants matching '${config.pattern.pattern}' found.")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { StringConstantFormatter.format(matches) },
            json = { JsonFormatter.formatStringConstants(matches) },
            llm = { LlmFormatter.formatStringConstants(matches) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        scope?.let { put("scope", it) }
    }
}
