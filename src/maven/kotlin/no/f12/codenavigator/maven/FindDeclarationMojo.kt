package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.navigation.declaration.DeclarationFilter
import no.f12.codenavigator.navigation.declaration.DeclarationFormatter
import no.f12.codenavigator.navigation.declaration.DeclarationScanner
import no.f12.codenavigator.navigation.declaration.FindDeclarationConfig
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.registry.BuildTool
import no.f12.codenavigator.registry.TaskRegistry
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-declaration", requiresProject = true)
class FindDeclarationMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "pattern")
    private var pattern: String? = null

    @Parameter(property = "kind")
    private var kind: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        val config = try {
            FindDeclarationConfig.parse(TaskRegistry.FIND_DECLARATION.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
        } catch (e: IllegalArgumentException) {
            println(OutputWrapper.emptyResult(OutputFormat.LLM, "find-declaration failed: ${e.message}",
                TaskRegistry.FIND_DECLARATION.renderExamples(BuildTool.MAVEN)))
            return
        }

        val sourceRoots = (
            project.mainSourceRoots().map { it to SourceSet.MAIN } +
                project.testSourceRoots().map { it to SourceSet.TEST }
            )
            .filter { (_, sourceSet) -> config.scope.matchesSourceSet(sourceSet) }
            .map { (dir, _) -> dir }

        val matches = DeclarationFilter.filter(DeclarationScanner.scan(sourceRoots), config.pattern, config.kind)
        if (matches.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No declarations matching '${config.pattern}' found.", noResultsHints(config)))
            return
        }
        println(OutputWrapper.formatAndWrap(config.format) { format -> DeclarationFormatter.format(matches, format) })
    }

    private fun noResultsHints(config: FindDeclarationConfig): List<String> = buildList {
        add("Unqualified patterns match the declaration's own name; use Class.member or a package prefix to match the full dotted name.")
        if (config.kind != null) add("Drop -Dkind to search all declaration kinds.")
        add("Only Kotlin (.kt) sources are scanned.")
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        pattern?.let { put("pattern", it) }
        kind?.let { put("kind", it) }
        scope?.let { put("scope", it) }
    }
}
