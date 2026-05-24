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

object DsmOutputFormatter {

    fun format(output: DistanceOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No inter-package dependencies found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format,
            text = { PackageDistanceFormatter.format(result) },
            json = { JsonFormatter.formatDistance(result) },
            llm = { LlmFormatter.formatDistance(result) },
        )
    }

    fun format(output: StrengthOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No inter-package dependencies found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format,
            text = { StrengthFormatter.format(result) },
            json = { JsonFormatter.formatStrength(result) },
            llm = { LlmFormatter.formatStrength(result) },
        )
    }

    fun format(output: CohesionOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No packages with dependencies found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format,
            text = { CohesionFormatter.format(result) },
            json = { JsonFormatter.formatCohesion(result) },
            llm = { LlmFormatter.formatCohesion(result) },
        )
    }

    fun format(output: MoveSuggestOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No misplaced classes found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format,
            text = { MoveSuggestFormatter.format(result) },
            json = { JsonFormatter.formatMoveSuggestions(result) },
            llm = { LlmFormatter.formatMoveSuggestions(result) },
        )
    }

    fun format(output: BalanceOutput, format: OutputFormat): String? {
        val result = output.result
            ?: return OutputWrapper.emptyResult(format, "No balanced coupling data found.", output.noResultsHints)

        return OutputWrapper.formatAndWrap(format,
            text = { BalanceFormatter.format(result) },
            json = { JsonFormatter.formatBalance(result) },
            llm = { LlmFormatter.formatBalance(result) },
        )
    }
}
