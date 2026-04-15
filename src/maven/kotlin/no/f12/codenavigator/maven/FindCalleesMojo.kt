package no.f12.codenavigator.maven

import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.CallDirection
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "find-callees")
@Execute(phase = LifecyclePhase.COMPILE)
class FindCalleesMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "pattern")
    private var pattern: String? = null

    @Parameter(property = "maxdepth")
    private var maxdepth: String? = null

    @Parameter(property = "project-only")
    private var projectOnly: String? = null

    @Parameter(property = "filter-synthetic")
    private var filterSynthetic: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    override fun execute() {
        CallTreeMojoSupport.execute(
            project = project,
            log = log,
            properties = buildPropertyMap(),
            taskDef = TaskRegistry.FIND_CALLEES,
            direction = CallDirection.CALLEES,
            usageHint = "Missing required property. Usage: mvn cnav:find-callees -Dpattern=<regex> -Dmaxdepth=3",
        )
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        maxdepth?.let { put("maxdepth", it) }
        projectOnly?.let { put("project-only", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
        scope?.let { put("scope", it) }
    }
}
