package no.f12.codenavigator.formatting

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.dsm.BalanceFormatter
import no.f12.codenavigator.navigation.dsm.BalanceOutput
import no.f12.codenavigator.navigation.dsm.CohesionFormatter
import no.f12.codenavigator.navigation.dsm.CohesionOutput
import no.f12.codenavigator.navigation.dsm.DistanceOutput
import no.f12.codenavigator.navigation.dsm.MoveSuggestFormatter
import no.f12.codenavigator.navigation.dsm.MoveSuggestOutput
import no.f12.codenavigator.navigation.dsm.PackageDistanceFormatter
import no.f12.codenavigator.navigation.dsm.StrengthFormatter
import no.f12.codenavigator.navigation.dsm.StrengthOutput
import no.f12.codenavigator.navigation.dsm.StructureFormatter
import no.f12.codenavigator.navigation.dsm.SuggestStructureOutput

object DsmOutputFormatter {

    fun format(output: DistanceOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No inter-package dependencies found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> PackageDistanceFormatter.format(result)
        OutputFormat.JSON -> PackageDistanceFormatter.formatJson(result)
        OutputFormat.LLM -> PackageDistanceFormatter.formatLlm(result)
    }
}
    }

    fun format(output: StrengthOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No inter-package dependencies found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> StrengthFormatter.format(result)
        OutputFormat.JSON -> StrengthFormatter.formatJson(result)
        OutputFormat.LLM -> StrengthFormatter.formatLlm(result)
    }
}
    }

    fun format(output: CohesionOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No packages with dependencies found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> CohesionFormatter.format(result)
        OutputFormat.JSON -> CohesionFormatter.formatJson(result)
        OutputFormat.LLM -> CohesionFormatter.formatLlm(result)
    }
}
    }

    fun format(output: MoveSuggestOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No misplaced classes found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> MoveSuggestFormatter.format(result)
        OutputFormat.JSON -> MoveSuggestFormatter.formatJson(result)
        OutputFormat.LLM -> MoveSuggestFormatter.formatLlm(result)
    }
}
    }

    fun format(output: BalanceOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No balanced coupling data found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> BalanceFormatter.format(result)
        OutputFormat.JSON -> BalanceFormatter.formatJson(result)
        OutputFormat.LLM -> BalanceFormatter.formatLlm(result)
    }
}
    }

    fun format(output: SuggestStructureOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No structural groups found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> StructureFormatter.formatText(result)
        OutputFormat.JSON -> StructureFormatter.formatJson(result)
        OutputFormat.LLM -> StructureFormatter.formatLlm(result)
    }
}
    }
}
