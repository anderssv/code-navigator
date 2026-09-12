package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.dsm.ProxyPortDetector
import no.f12.codenavigator.navigation.dsm.RingsAnalysis
import no.f12.codenavigator.navigation.dsm.RingsOrchestrator
import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File

data class TestCouplingOutput(
    val result: TestCouplingResult?,
    val skippedFileWarning: String?,
    val noPortsFound: Boolean,
)

object TestCouplingOrchestrator {

    fun run(
        config: TestCouplingTaskConfig,
        taggedDirs: List<Pair<File, SourceSet>>,
        cacheDir: File,
        reportFile: File,
        projectDir: File,
    ): TestCouplingOutput {
        val classDirectories = taggedDirs.map { it.first }

        val callGraphCacheFile = File(cacheDir, "call-graph.cache")
        val callGraphResult = CallGraphCache.getOrBuildTagged(callGraphCacheFile, taggedDirs)
        SkippedFileReporter.report(callGraphResult.skippedFiles, reportFile)
        val graph = callGraphResult.data

        val interfaceCacheFile = File(cacheDir, "interface-registry-all.cache")
        val interfaceResult = InterfaceRegistryCache.getOrBuild(interfaceCacheFile, classDirectories)
        val interfaceRegistry = interfaceResult.data

        val portInterfaces = interfaceRegistry.findInterfaces(config.ports.pattern)
        if (portInterfaces.isEmpty()) {
            return TestCouplingOutput(
                result = null,
                skippedFileWarning = SkippedFileReporter.report(callGraphResult.skippedFiles, reportFile),
                noPortsFound = true,
            )
        }

        // Only computed when the adapter subject is actually requested — cnavRings' own pipeline
        // (dependency extraction, adapter/service-tier classification) is real work, no need to pay
        // for it on the default (tests-only) path.
        val candidateAdapterClasses: Set<ClassName> = if (CouplingSubjectKind.ADAPTER in config.subjects) {
            driveAdapterCandidates(taggedDirs, projectDir, reportFile)
        } else {
            emptySet()
        }

        val couplingConfig = TestCouplingConfig(
            ports = config.ports,
            exclude = config.exclude,
            subjects = config.subjects,
            candidateAdapterClasses = candidateAdapterClasses,
            writeMethods = config.writeMethods,
            readMethods = config.readMethods,
        )
        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, couplingConfig)

        return TestCouplingOutput(
            result = result,
            skippedFileWarning = SkippedFileReporter.report(callGraphResult.skippedFiles, reportFile),
            noPortsFound = false,
        )
    }

    /**
     * A driving-adapter candidate is any class `cnavRings` classifies as an adapter (`ioClasses`)
     * that isn't itself service-tier and isn't a framework-generated proxy (`$GeneratedProxy` —
     * synthetic, never a real caller). `TestCouplingBuilder.analyze` further narrows this to exclude
     * implementors of the `--ports` regex specifically, since that's a per-check concept `cnavRings`
     * doesn't know about.
     */
    private fun driveAdapterCandidates(
        taggedDirs: List<Pair<File, SourceSet>>,
        projectDir: File,
        reportFile: File,
    ): Set<ClassName> {
        val analysis = RingsOrchestrator.run(taggedDirs, Scope.PROD, bootstrap = false, plan = emptyList(), projectDir = projectDir, reportFile = reportFile)
        val output = (analysis as? RingsAnalysis.Hexagonal)?.output ?: return emptySet()
        return output.graph.ioClasses - output.serviceTier - output.graph.ioClasses.filter { it.value.endsWith(ProxyPortDetector.PROXY_SUFFIX) }.toSet()
    }
}

