package no.f12.codenavigator.maven

import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import no.f12.codenavigator.formatting.OutputWrapper
import no.f12.codenavigator.registry.TaskRegistry
import no.f12.codenavigator.analysis.GitLogRunner
import no.f12.codenavigator.analysis.HotspotBuilder
import no.f12.codenavigator.analysis.PackageVolatilityBuilder
import no.f12.codenavigator.analysis.PackageVolatilityFormatter
import no.f12.codenavigator.analysis.VolatilityConfig
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "volatility")
class PackageVolatilityMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    @Parameter(property = "format")
    private var format: String? = null

    @Parameter(property = "llm")
    private var llm: String? = null

    @Parameter(property = "after")
    private var after: String? = null

    @Parameter(property = "min-revs")
    private var minRevs: String? = null

    @Parameter(property = "top")
    private var top: String? = null

    @Parameter(property = "no-follow")
    private var noFollow: Boolean = false

    override fun execute() {
        val config = VolatilityConfig.parse(TaskRegistry.VOLATILITY.enhanceProperties(buildPropertyMap()))

        val commits = GitLogRunner.run(project.basedir, config.after, followRenames = config.followRenames)
        val hotspots = HotspotBuilder.build(commits, config.minRevs)
        val result = PackageVolatilityBuilder.build(hotspots, config.top)

        if (result.entries.isEmpty()) {
            val hints = PackageVolatilityFormatter.noResultsHints()
            println(OutputWrapper.emptyResult(config.format, "No package volatility data found.", hints))
            return
        }

        println(OutputWrapper.formatAndWrap(config.format,
            text = { PackageVolatilityFormatter.format(result) },
            json = { JsonFormatter.formatVolatility(result) },
            llm = { LlmFormatter.formatVolatility(result) },
        ))
    }

    private fun buildPropertyMap(): Map<String, String?> = buildMap {
        format?.let { put("format", it) }
        llm?.let { put("llm", it) }
        after?.let { put("after", it) }
        minRevs?.let { put("min-revs", it) }
        top?.let { put("top", it) }
        if (noFollow) put("no-follow", null)
    }
}
