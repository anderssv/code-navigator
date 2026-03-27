package no.f12.codenavigator.maven

import no.f12.codenavigator.JsonFormatter
import no.f12.codenavigator.LlmFormatter
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.OutputWrapper
import no.f12.codenavigator.navigation.AnnotationExtractor
import no.f12.codenavigator.navigation.CallGraphBuilder
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.DeadCodeConfig
import no.f12.codenavigator.navigation.DeadCodeFinder
import no.f12.codenavigator.navigation.DeadCodeFormatter
import no.f12.codenavigator.navigation.FieldExtractor
import no.f12.codenavigator.navigation.InterfaceRegistry
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "dead")
@Execute(phase = LifecyclePhase.COMPILE)
class DeadCodeMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "filter")
    private var filter: String? = null

    @Parameter(property = "exclude")
    private var exclude: String? = null

    @Parameter(property = "classes-only")
    private var classesOnly: String? = null

    @Parameter(property = "exclude-annotated")
    private var excludeAnnotated: String? = null

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val config = DeadCodeConfig.parse(buildPropertyMap())

        val result = CallGraphBuilder.build(listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val excludeAnnotatedSet = config.excludeAnnotated.toSet()
        val (classAnnotations, methodAnnotations) = if (excludeAnnotatedSet.isNotEmpty()) {
            AnnotationExtractor.scanAll(listOf(classesDir))
        } else {
            Pair(emptyMap(), emptyMap())
        }

        val testClassesDir = File(project.build.testOutputDirectory)
        val testGraph = if (testClassesDir.exists()) {
            CallGraphBuilder.build(listOf(testClassesDir)).data
        } else {
            null
        }

        val interfaceRegistry = InterfaceRegistry.build(listOf(classesDir)).data
        val interfaceImplementors = mutableMapOf<ClassName, MutableSet<ClassName>>()
        interfaceRegistry.forEachEntry { interfaceName, implementors ->
            interfaceImplementors[interfaceName] = implementors.map { it.className }.toMutableSet()
        }

        val classFields = FieldExtractor.scanAll(listOf(classesDir))

        val dead = DeadCodeFinder.find(
            graph = graph,
            filter = config.filter,
            exclude = config.exclude,
            classesOnly = config.classesOnly,
            excludeAnnotated = excludeAnnotatedSet,
            classAnnotations = classAnnotations,
            methodAnnotations = methodAnnotations,
            testGraph = testGraph,
            interfaceImplementors = interfaceImplementors,
            classFields = classFields,
        )

        if (dead.isEmpty()) {
            println("No potential dead code found.")
            return
        }

        val output = when (config.format) {
            OutputFormat.JSON -> JsonFormatter.formatDead(dead)
            OutputFormat.LLM -> LlmFormatter.formatDead(dead)
            OutputFormat.TEXT -> DeadCodeFormatter.format(dead)
        }
        println(OutputWrapper.wrap(output, config.format))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        filter?.let { put("filter", it) }
        exclude?.let { put("exclude", it) }
        classesOnly?.let { put("classes-only", it) }
        excludeAnnotated?.let { put("exclude-annotated", it) }
    }
}
