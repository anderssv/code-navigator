package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.core.SourceSetResolver
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

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val config = try {
            TypeHierarchyConfig.parse(TaskRegistry.TYPE_HIERARCHY.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(e.message)
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        if (resolver.classDirectories.isEmpty() || resolver.classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val allResults = TypeHierarchyBuilder.build(
            resolver.classDirectories,
            config.pattern,
            config.projectOnly,
        )
        val results = allResults.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }

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
        scope?.let { put("scope", it) }
    }
}
