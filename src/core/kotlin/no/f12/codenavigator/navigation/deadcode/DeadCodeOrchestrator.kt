package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.annotation.AnnotationExtractor
import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
import java.io.File

/**
 * Shared dead code orchestration used by both DeadCodeTask/Mojo and MetricsTask/Mojo.
 * Ensures consistent scanning, query building, and framework preset handling.
 */
object DeadCodeOrchestrator {

    data class DeadCodeInput(
        val graph: CallGraph,
        val classDirectories: List<File>,
        val testGraph: CallGraph?,
        val excludeAnnotated: Set<String>,
        val modifierAnnotated: Set<String> = emptySet(),
        val supertypeEntryPoints: Set<ClassName> = emptySet(),
        val receiverTypeEntryPoints: Set<ClassName> = emptySet(),
        val scope: Scope = Scope.ALL,
        val filter: Regex? = null,
        val exclude: Regex? = null,
        val classesOnly: Boolean = false,
        val cacheDir: File,
        val minConfidence: DeadCodeConfidence = DeadCodeConfidence.LOW,
        val ignoreSuppress: Boolean = true,
    )

    fun findDeadCode(input: DeadCodeInput): List<DeadCode> {
        val annotations = AnnotationExtractor.scanAll(input.classDirectories)

        val interfaceRegistry = InterfaceRegistryCache.getOrBuild(
            File(input.cacheDir, "interface-registry.cache"),
            input.classDirectories,
        ).data
        val interfaceImplementors = mutableMapOf<ClassName, MutableSet<ClassName>>()
        interfaceRegistry.forEachEntry { interfaceName, implementors ->
            interfaceImplementors[interfaceName] = implementors.map { it.className }.toMutableSet()
        }

        val classFields = FieldExtractor.scanAll(input.classDirectories)
        val inlineMethods = InlineMethodDetector.scanAll(input.classDirectories)
        val delegationMethods = DelegationMethodDetector.scanAll(input.classDirectories)
        val bridgeMethods = BridgeMethodDetector.scanAll(input.classDirectories)
        val classExternalInterfaces = interfaceRegistry.externalInterfacesOf(input.graph.projectClasses())
        val classReceiverTypes = ReceiverTypeExtractor.scanAll(input.classDirectories)

        return DeadCodeFinder.find(DeadCodeQuery(
            graph = input.graph,
            filter = input.filter,
            exclude = input.exclude,
            classesOnly = input.classesOnly,
            excludeAnnotated = input.excludeAnnotated,
            classAnnotations = annotations.classAnnotations,
            methodAnnotations = annotations.methodAnnotations,
            classAnnotationParameters = annotations.classAnnotationParameters,
            methodAnnotationParameters = annotations.methodAnnotationParameters,
            ignoreSuppress = input.ignoreSuppress,
            testGraph = input.testGraph,
            interfaceImplementors = interfaceImplementors,
            classFields = classFields,
            inlineMethods = inlineMethods,
            classExternalInterfaces = classExternalInterfaces,
            scope = input.scope,
            modifierAnnotated = input.modifierAnnotated,
            supertypeEntryPoints = input.supertypeEntryPoints,
            testClasses = input.testGraph?.projectClasses() ?: emptySet(),
            classReceiverTypes = classReceiverTypes,
            receiverTypeEntryPoints = input.receiverTypeEntryPoints,
            delegationMethods = delegationMethods,
            bridgeMethods = bridgeMethods,
            declaredMethods = input.graph.allDeclaredMethods(),
        )).let { filterByConfidence(it, input.minConfidence) }
    }

    /**
     * Keep only findings at or above [minConfidence]. The confidence enum is ordered
     * HIGH, MEDIUM, LOW, so a higher confidence has a LOWER ordinal — "at or above the
     * threshold" therefore keeps findings whose ordinal <= the threshold's ordinal.
     */
    internal fun filterByConfidence(findings: List<DeadCode>, minConfidence: DeadCodeConfidence): List<DeadCode> =
        findings.filter { it.confidence.ordinal <= minConfidence.ordinal }
}
