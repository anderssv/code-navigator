package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.deadcode.BridgeMethodDetector
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfig
import no.f12.codenavigator.navigation.deadcode.DeadCodeFinder
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.deadcode.DelegationMethodDetector
import no.f12.codenavigator.navigation.deadcode.FieldExtractor
import no.f12.codenavigator.navigation.deadcode.InlineMethodDetector
import no.f12.codenavigator.navigation.deadcode.ReceiverTypeExtractor
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache
import no.f12.codenavigator.navigation.SkippedFileReporter
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "dead")
@Execute(phase = LifecyclePhase.TEST_COMPILE)
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

    @Parameter(property = "prod-only")
    private var prodOnly: String? = null

    @Parameter(property = "treat-as-dead")
    private var treatAsDead: String? = null

    @Parameter(property = "test-only")
    private var testOnly: String? = null

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists()) {
            log.warn("Classes directory does not exist: $classesDir — run 'mvn compile' first.")
            return
        }

        val config = DeadCodeConfig.parse(TaskRegistry.DEAD.enhanceProperties(buildPropertyMap()))

        val result = CallGraphCache.getOrBuild(File(project.build.directory, "cnav/call-graph.cache"), listOf(classesDir))
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val graph = result.data

        val excludeAnnotatedSet = config.excludeAnnotated.toSet()
        val annotations = AnnotationExtractor.scanAll(listOf(classesDir))

        val testClassesDir = File(project.build.testOutputDirectory)
        val testGraph = if (testClassesDir.exists()) {
            CallGraphCache.getOrBuild(File(project.build.directory, "cnav/test-call-graph.cache"), listOf(testClassesDir)).data
        } else {
            null
        }

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(File(project.build.directory, "cnav/interface-registry.cache"), listOf(classesDir)).data
        val interfaceImplementors = mutableMapOf<ClassName, MutableSet<ClassName>>()
        interfaceRegistry.forEachEntry { interfaceName, implementors ->
            interfaceImplementors[interfaceName] = implementors.map { it.className }.toMutableSet()
        }

        val classFields = FieldExtractor.scanAll(listOf(classesDir))

        val inlineMethods = InlineMethodDetector.scanAll(listOf(classesDir))

        val delegationMethods = DelegationMethodDetector.scanAll(listOf(classesDir))

        val bridgeMethods = BridgeMethodDetector.scanAll(listOf(classesDir))

        val classExternalInterfaces = interfaceRegistry.externalInterfacesOf(graph.projectClasses())

        val classReceiverTypes = ReceiverTypeExtractor.scanAll(listOf(classesDir))

        val dead = DeadCodeFinder.find(
            graph = graph,
            filter = config.filter,
            exclude = config.exclude,
            classesOnly = config.classesOnly,
            excludeAnnotated = excludeAnnotatedSet,
            classAnnotations = annotations.classAnnotations,
            methodAnnotations = annotations.methodAnnotations,
            testGraph = testGraph,
            interfaceImplementors = interfaceImplementors,
            classFields = classFields,
            inlineMethods = inlineMethods,
            classExternalInterfaces = classExternalInterfaces,
            prodOnly = config.prodOnly,
            modifierAnnotated = config.modifierAnnotated.toSet(),
            supertypeEntryPoints = config.supertypeEntryPoints,
            testClasses = testGraph?.projectClasses() ?: emptySet(),
            classReceiverTypes = classReceiverTypes,
            receiverTypeEntryPoints = config.receiverTypeEntryPoints,
            testOnly = config.testOnly,
            delegationMethods = delegationMethods,
            bridgeMethods = bridgeMethods,
        )

        if (dead.isEmpty()) {
            println("No potential dead code found.")
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { DeadCodeFormatter.format(dead) },
            json = { JsonFormatter.formatDead(dead) },
            llm = { LlmFormatter.formatDead(dead) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        filter?.let { put("filter", it) }
        exclude?.let { put("exclude", it) }
        classesOnly?.let { put("classes-only", it) }
        excludeAnnotated?.let { put("exclude-annotated", it) }
        prodOnly?.let { put("prod-only", it) }
        treatAsDead?.let { put("treat-as-dead", it) }
        testOnly?.let { put("test-only", it) }
    }
}
