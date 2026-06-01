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
        OutputFormat.JSON -> JsonFormatter.formatDistance(result)
        OutputFormat.LLM -> LlmFormatter.formatDistance(result)
    }
}
    }

    fun format(output: StrengthOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No inter-package dependencies found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> StrengthFormatter.format(result)
        OutputFormat.JSON -> JsonFormatter.formatStrength(result)
        OutputFormat.LLM -> LlmFormatter.formatStrength(result)
    }
}
    }

    fun format(output: CohesionOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No packages with dependencies found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> CohesionFormatter.format(result)
        OutputFormat.JSON -> JsonFormatter.formatCohesion(result)
        OutputFormat.LLM -> LlmFormatter.formatCohesion(result)
    }
}
    }

    fun format(output: MoveSuggestOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No misplaced classes found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> MoveSuggestFormatter.format(result)
        OutputFormat.JSON -> JsonFormatter.formatMoveSuggestions(result)
        OutputFormat.LLM -> LlmFormatter.formatMoveSuggestions(result)
    }
}
    }

    fun format(output: BalanceOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No balanced coupling data found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format) { format ->
    when (format) {
        OutputFormat.TEXT, OutputFormat.DIFF -> BalanceFormatter.format(result)
        OutputFormat.JSON -> JsonFormatter.formatBalance(result)
        OutputFormat.LLM -> LlmFormatter.formatBalance(result)
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
