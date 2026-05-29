package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.bytecode.SkippedFileReporter
import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistryCache
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

        val couplingConfig = TestCouplingConfig(ports = config.ports)
        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, couplingConfig)

        return TestCouplingOutput(
            result = result,
            skippedFileWarning = SkippedFileReporter.report(callGraphResult.skippedFiles, reportFile),
            noPortsFound = false,
        )
    }
}
