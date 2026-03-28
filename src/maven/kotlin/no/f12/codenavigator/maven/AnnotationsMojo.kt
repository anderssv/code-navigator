package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.AnnotationQueryBuilder
import no.f12.codenavigator.navigation.AnnotationQueryConfig
import no.f12.codenavigator.navigation.AnnotationQueryFormatter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "annotations")
@Execute(phase = LifecyclePhase.COMPILE)
class AnnotationsMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "pattern")
    private var pattern: String? = null

    @Parameter(property = "methods")
    private var methods: String? = null

    override fun execute() {
        val config = try {
            AnnotationQueryConfig.parse(buildPropertyMap())
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "Missing required property. Usage: mvn cnav:annotations -Dpattern=<regex> [-Dmethods=true]",
            )
        }

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val matches = AnnotationQueryBuilder.query(listOf(classesDir), config.pattern, config.methods)

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatAnnotations(matches)
            OutputFormat.LLM -> LlmFormatter.formatAnnotations(matches)
            OutputFormat.TEXT -> AnnotationQueryFormatter.format(matches)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        methods?.let { put("methods", it) }
    }
}
