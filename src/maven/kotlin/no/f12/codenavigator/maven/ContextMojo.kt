package no.f12.codenavigator.maven

import no.f12.codenavigator.config.OutputFormat

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeBuilder
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.classinfo.ClassDetailScanner
import no.f12.codenavigator.navigation.context.ContextBuilder
import no.f12.codenavigator.navigation.context.ContextConfig
import no.f12.codenavigator.navigation.context.ContextFormatter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "context")
@Execute(phase = LifecyclePhase.COMPILE)
class ContextMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null


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
        project.checkStaleness(log)

        val config = try {
            ContextConfig.parse(TaskRegistry.CONTEXT.enhanceProperties(project.applyConfigDefaults(buildPropertyMap())))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "Missing required property 'pattern'. Usage: mvn cnav:context -Dpattern=<regex>",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.isEmpty()) {
            log.warn("Classes directory does not exist: ${File(project.build.outputDirectory)} — run 'mvn compile' first.")
            return
        }
        val classDirectories = taggedDirs.map { it.first }

        val classResult = ClassDetailScanner.scan(classDirectories, config.pattern)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(classResult.skippedFiles, reportFile)?.let { log.warn(it) }
        val matchingDetails = classResult.data

        if (matchingDetails.isEmpty()) {
            println(OutputWrapper.emptyResult(config.format, "No classes found matching '${config.pattern}'"))
            return
        }

        val graphResult = CallGraphCache.getOrBuildTagged(
            File(project.build.directory, "cnav/call-graph.cache"),
            taggedDirs,
        )
        SkippedFileReporter.report(graphResult.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = graphResult.data

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(
            File(project.build.directory, "cnav/interface-registry.cache"),
            classDirectories,
        ).data
        val interfaceImplementors = interfaceRegistry.implementorMap()
        val classToInterfaces = interfaceRegistry.classToInterfacesMap()

        val annotations = AnnotationExtractor.scanAll(classDirectories)
        val filter = config.buildFilter(graph)

        val results = matchingDetails.map { classDetail ->
            val methods = classDetail.methods.map { method ->
                MethodRef(classDetail.className, method.name)
            }

            val callers = CallTreeBuilder.build(
                graph, methods, config.maxDepth, CallDirection.CALLERS, filter,
                interfaceImplementors = interfaceImplementors,
                classToInterfaces = classToInterfaces,
                classAnnotations = annotations.classAnnotations,
                methodAnnotations = annotations.methodAnnotations,
                classAnnotationParameters = annotations.classAnnotationParameters,
                methodAnnotationParameters = annotations.methodAnnotationParameters,
            )

            val callees = CallTreeBuilder.build(
                graph, methods, config.maxDepth, CallDirection.CALLEES, filter,
                interfaceImplementors = interfaceImplementors,
                classToInterfaces = classToInterfaces,
                classAnnotations = annotations.classAnnotations,
                methodAnnotations = annotations.methodAnnotations,
                classAnnotationParameters = annotations.classAnnotationParameters,
                methodAnnotationParameters = annotations.methodAnnotationParameters,
            )

            val implementors = interfaceRegistry.implementorsOf(classDetail.className)
            val implementedInterfaces = interfaceRegistry.interfacesOf(classDetail.className).sorted().toList()

            ContextBuilder.build(
                classDetail = classDetail,
                callers = callers,
                callees = callees,
                implementors = implementors,
                implementedInterfaces = implementedInterfaces,
            )
        }

        println(
            OutputWrapper.formatAndWrap(config.format) { format ->
                when (format) {
                    OutputFormat.TEXT, OutputFormat.DIFF -> results.joinToString("\n\n") { ContextFormatter.format(it) }
                    OutputFormat.JSON -> "[${results.joinToString(",") { JsonFormatter.formatContext(it) }}]"
                    OutputFormat.LLM -> results.joinToString("\n\n") { LlmFormatter.formatContext(it) }
                }
            },
        )
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        pattern?.let { put("pattern", it) }
        maxdepth?.let { put("maxdepth", it) }
        projectOnly?.let { put("project-only", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
        scope?.let { put("scope", it) }
    }
}
