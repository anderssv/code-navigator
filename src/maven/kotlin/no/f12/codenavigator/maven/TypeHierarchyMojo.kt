package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.bytecode.SourceSetResolver
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyBuilder
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyConfig
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyFormatter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.Path

@Mojo(name = "type-hierarchy", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.COMPILE)
class TypeHierarchyMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


    @Parameter(property = "pattern", required = true)
    private var pattern: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        project.checkStaleness(log)

        val config = try {
            TypeHierarchyConfig.parse(TaskRegistry.TYPE_HIERARCHY.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(e.message)
        }

        val taggedDirs = project.taggedClassDirectories()
        val resolver = SourceSetResolver.from(taggedDirs)

        if (resolver.classDirectories.isEmpty() || resolver.classDirectories.none { it.exists() }) {
            log.warn("Classes directory does not exist — run 'mvn compile' first.")
            return
        }

        val classpath: List<Path> = if (!config.projectOnly) {
            @Suppress("UNCHECKED_CAST")
            (project.runtimeClasspathElements as? List<String> ?: emptyList())
                .map { File(it).toPath() }
        } else {
            emptyList()
        }

        val allResults = TypeHierarchyBuilder.build(
            resolver.classDirectories,
            config.pattern,
            config.projectOnly,
            classpath,
        )
        val results = allResults.filter { resolver.sourceSetOf(it.className)?.let { ss -> config.scope.matchesSourceSet(ss) } ?: true }

        if (results.isEmpty()) {
            println("No classes found matching '${config.pattern}'")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> TypeHierarchyFormatter.format(results)
        OutputFormat.JSON -> JsonFormatter.formatTypeHierarchy(results)
        OutputFormat.LLM -> LlmFormatter.formatTypeHierarchy(results)
    }
})
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        pattern?.let { put("pattern", it) }
        projectOnly?.let { put("project-only", it) }
        scope?.let { put("scope", it) }
    }
}
