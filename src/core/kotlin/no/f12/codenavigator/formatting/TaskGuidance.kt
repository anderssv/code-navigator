package no.f12.codenavigator.formatting

data class TaskGuidance(
    val purpose: String,
    val parameterGuidance: String,
    val interpretation: String,
) {
    fun render(): String = buildString {
        if (purpose.isNotBlank()) appendLine("Purpose: $purpose")
        if (parameterGuidance.isNotBlank()) appendLine("Parameters: $parameterGuidance")
        if (interpretation.isNotBlank()) appendLine("Interpretation: $interpretation")
    }.trimEnd()
}
