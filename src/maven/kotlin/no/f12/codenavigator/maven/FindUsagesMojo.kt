package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.navigation.callgraph.FindUsagesConfig
import no.f12.codenavigator.navigation.callgraph.SmartUsageResult
import no.f12.codenavigator.navigation.callgraph.UsageCollapser
import no.f12.codenavigator.navigation.core.GroupBy
import no.f12.codenavigator.navigation.core.SkippedFileReporter
import no.f12.codenavigator.navigation.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.callgraph.UsageScanner
import no.f12.codenavigator.navigation.core.TypeMatcher
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistryCache
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "find-usages")
@Execute(phase = LifecyclePhase.COMPILE)
class FindUsagesMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "owner-class")
    private var ownerClass: String? = null

    @Parameter(property = "method")
    private var method: String? = null

    @Parameter(property = "field")
    private var field: String? = null

    @Parameter(property = "type")
    private var type: String? = null

    @Parameter(property = "outside-package")
    private var outsidePackage: String? = null

    @Parameter(property = "scope")
    private var scope: String? = null

    @Parameter(property = "filter-synthetic")
    private var filterSynthetic: String? = null

    @Parameter(property = "group-by")
    private var groupBy: String? = null

    @Parameter(property = "raw")
    private var raw: String? = null

    @Parameter(property = "include-impls")
    private var includeImpls: String? = null

    override fun execute() {
        val config = try {
            FindUsagesConfig.parse(TaskRegistry.FIND_USAGES.enhanceProperties(buildPropertyMap()))
        } catch (e: IllegalArgumentException) {
            throw MojoFailureException(
                "${e.message}\n" +
                    "Usage: mvn cnav:find-usages -Downer-class=<class> [-Dmethod=<name>] [-Dfield=<name>]\n" +
                    "       mvn cnav:find-usages -Dtype=<class>",
            )
        }

        val taggedDirs = project.taggedClassDirectories()
        if (taggedDirs.isEmpty()) {
            log.warn("Classes directory does not exist: ${File(project.build.outputDirectory)} — run 'mvn compile' first.")
            return
        }

        val ownerMatcher = config.ownerClass?.let { TypeMatcher.fromPattern(it) }
        val typeMatcher = config.type?.let { TypeMatcher.fromPattern(it) }

        val result = UsageScanner.scanTagged(taggedDirs, ownerMatcher = ownerMatcher, method = config.method, field = config.field, typeMatcher = typeMatcher)
        val reportFile = File(project.build.directory, "cnav/skipped-files.txt")
        SkippedFileReporter.report(result.skippedFiles, reportFile)?.let { log.warn(it) }
        val afterPackageFilter = UsageScanner.filterOutsidePackage(result.data, config.outsidePackage)
        val afterSyntheticFilter = config.filterSyntheticCallers(afterPackageFilter)
        var usages = config.filterBySourceSet(afterSyntheticFilter)

        // Smart usages: detect interface and include implementations
        val classDirectories = taggedDirs.map { it.first }
        val targetType = config.type ?: config.ownerClass
        val interfaceRegistry = if (targetType != null) {
            val cacheFile = File(project.build.directory, "cnav/interface-registry.cache")
            InterfaceRegistryCache.getOrBuild(cacheFile, classDirectories).data
        } else null

        // Use findInterfaces() — same regex-based containsMatchIn resolution as all other commands
        val matchedInterfaces = if (interfaceRegistry != null && targetType != null) {
            interfaceRegistry.findInterfaces(targetType)
        } else emptyList()

        val implementations = matchedInterfaces.flatMap { interfaceRegistry!!.implementorsOf(it) }

        if (config.includeImpls && implementations.isNotEmpty()) {
            for (impl in implementations) {
                val implResult = UsageScanner.scanTagged(taggedDirs, ownerClass = impl.className.value, method = config.method, field = config.field, type = null)
                val implFiltered = config.filterBySourceSet(config.filterSyntheticCallers(UsageScanner.filterOutsidePackage(implResult.data, config.outsidePackage)))
                usages = usages + implFiltered
            }
        }

        if (usages.isEmpty() && implementations.isEmpty()) {
            val target = UsageFormatter.noResultsTarget(config.ownerClass, config.method, config.field, config.type)
            val hints = UsageFormatter.noResultsHints(config.ownerClass, config.method, config.field, config.type)
            println(OutputWrapper.emptyResult(config.format, "No usages found for '$target'.", hints))
            return
        }

        val interfaceTypeSet = matchedInterfaces.toSet()
        val collapsed = if (!config.raw) UsageCollapser.collapse(usages, interfaceTypeSet) else emptyList()

        // Derive matched types from collapsed output — use topLevelClass to merge inner classes
        val matchedTypes = collapsed.map { it.targetOwner.topLevelClass() }.distinct().sorted()
        val smartResult = SmartUsageResult(implementations, usages, matchedTypes, interfaceTypeSet)

        println(OutputWrapper.formatAndWrap(config.format,
            text = {
                when {
                    config.groupBy == GroupBy.FILE -> UsageFormatter.formatSummary(usages)
                    config.raw -> UsageFormatter.format(usages)
                    else -> UsageFormatter.formatSmartUsages(smartResult, collapsed)
                }
            },
            json = {
                when {
                    config.groupBy == GroupBy.FILE -> JsonFormatter.formatUsagesSummary(usages)
                    config.raw -> JsonFormatter.formatUsages(usages)
                    else -> JsonFormatter.formatSmartUsages(smartResult, collapsed)
                }
            },
            llm = {
                when {
                    config.groupBy == GroupBy.FILE -> LlmFormatter.formatUsagesSummary(usages)
                    config.raw -> LlmFormatter.formatUsages(usages)
                    else -> LlmFormatter.formatSmartUsages(smartResult, collapsed)
                }
            },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        ownerClass?.let { put("owner-class", it) }
        method?.let { put("method", it) }
        field?.let { put("field", it) }
        type?.let { put("type", it) }
        outsidePackage?.let { put("outside-package", it) }
        scope?.let { put("scope", it) }
        filterSynthetic?.let { put("filter-synthetic", it) }
        groupBy?.let { put("group-by", it) }
        raw?.let { put("raw", it) }
        includeImpls?.let { put("include-impls", it) }
    }
}
