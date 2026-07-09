package no.f12.codenavigator.formatting

/**
 * Shared by [LlmFormatter] and the per-feature LLM formatters it delegates to (e.g.
 * `CyclesFormatter.formatLlm`). `internal` so feature packages can append the same
 * "Interpretation: ..." footer convention without duplicating it.
 */
internal fun String.withInterpretation(interpretation: String): String =
    if (isEmpty()) this else "$this\n\n$interpretation"
