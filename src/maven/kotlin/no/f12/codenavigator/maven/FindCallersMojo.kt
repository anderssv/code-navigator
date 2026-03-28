package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.TaskRegistry
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallGraphBuilder
import no.f12.codenavigator.navigation.CallGraphConfig
import no.f12.codenavigator.navigation.CallTreeBuilder
import no.f12.codenavigator.navigation.CallTreeFormatter
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistry
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-callers")
@Execute(phase = LifecyclePhase.COMPILE)
class FindCallersMojo : AbstractMojo() {

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

    override fun execute() {
        val config = try {
            CallGraphConfig.parse(TaskRegistry.FIND_CALLERS.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "Missing required property. Usage: mvn cnav:find-callers -Dpattern=<regex> -Dmaxdepth=3",
            )
        }

        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val result = CallGraphBuilder.build(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data
        val methods = graph.findMethods(config.method)

        if (methods.isEmpty()) {
            println("No methods found matching '${config.method}'")
            return
        }

        val interfaceRegistry = InterfaceRegistry.build(listOf(classesDir)).data
        val interfaceImplementors = interfaceRegistry.implementorMap()
        val classToInterfaces = interfaceRegistry.classToInterfacesMap()

        val (classAnnotations, methodAnnotations) = AnnotationExtractor.scanAll(listOf(classesDir))

        val trees = CallTreeBuilder.build(
            graph, methods, config.maxDepth, CallDirection.CALLERS, config.buildFilter(graph),
            interfaceImplementors = interfaceImplementors,
            classToInterfaces = classToInterfaces,
            classAnnotations = classAnnotations,
            methodAnnotations = methodAnnotations,
        )
        println(OutputWrapper.formatAndWrap(config.format,
            text = { CallTreeFormatter.renderTrees(trees, CallDirection.CALLERS) },
            json = { JsonFormatter.renderCallTrees(trees) },
            llm = { LlmFormatter.renderCallTrees(trees, CallDirection.CALLERS) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        pattern?.let { put("pattern", it) }
        maxdepth?.let { put("maxdepth", it) }
        projectOnly?.let { put("project-only", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
    }
}
