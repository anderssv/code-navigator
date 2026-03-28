package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyBuilder
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyConfig
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyFormatter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "type-hierarchy")
@Execute(phase = LifecyclePhase.COMPILE)
class TypeHierarchyMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "pattern", required = true)
    private var pattern: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    override fun execute() {
        val config = try {
            TypeHierarchyConfig.parse(TaskRegistry.TYPE_HIERARCHY.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(e.message)
        }

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val results = TypeHierarchyBuilder.build(
            listOf(classesDir),
            config.pattern,
            config.projectOnly,
        )

        if (results.isEmpty()) {
            println("No classes found matching '${config.pattern}'")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { TypeHierarchyFormatter.format(results) },
            json = { JsonFormatter.formatTypeHierarchy(results) },
            llm = { LlmFormatter.formatTypeHierarchy(results) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        projectOnly?.let { put("project-only", it) }
    }
}
