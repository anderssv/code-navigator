package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject

object AmbientFormatter {

    const val NO_FINDINGS = "No ambient input found in the domain. Domain classes take time, randomness and configuration as input."

    fun formatText(result: AmbientResult, detail: Boolean): String {
        if (result.all.isEmpty()) return NO_FINDINGS

        return buildString {
            appendLine(header(result))
            appendLine(conventionLine(result))

            result.violations
                .groupBy { it.category }
                .toSortedMap()
                .forEach { (category, calls) ->
                    appendLine()
                    appendLine("$category (${calls.size})")
                    if (detail) {
                        calls.forEach { appendLine("  ${callLine(it)}") }
                    } else {
                        calls.groupBy { it.callerClass.topLevelClass() }.forEach { (owner, ownerCalls) ->
                            appendLine("  ${owner.simpleName()}  ${ownerCalls.joinToString(", ") { "${it.signal.label} ${it.location()}" }}")
                        }
                    }
                }

            if (result.initializerViolations.isNotEmpty()) {
                appendLine()
                appendLine("Default arguments and initializers (${result.initializerViolations.size}) — usually the cheapest fix: take the value as a parameter instead")
                result.initializerViolations.forEach { appendLine("  ${callLine(it)}") }
            }
        }.trimEnd()
    }

    fun formatLlm(result: AmbientResult): String {
        if (result.all.isEmpty()) return NO_FINDINGS

        return buildString {
            appendLine(header(result))
            appendLine(conventionLine(result))
            result.all.forEach { call ->
                val site = if (call.site == AmbientSite.INITIALIZER) " [initializer]" else ""
                appendLine("${call.category} ${call.callerClass.simpleName()}.${call.callerMethod} -> ${call.signal.label} ${call.location()}$site")
            }
        }.trimEnd()
    }

    fun formatJson(result: AmbientResult): String =
        jsonObject(
            "domainClassCount" to result.domainClassCount,
            "subjectSource" to result.subjectSource.name,
            "clockConvention" to result.clockConvention.name,
            "clockInjectingClasses" to result.clockInjectingClasses.size,
            "findings" to JsonRaw(
                jsonArray(result.all) { call ->
                    jsonObject(
                        "category" to call.category.name,
                        "class" to call.callerClass.value,
                        "method" to call.callerMethod,
                        "call" to call.signal.label,
                        "descriptor" to call.descriptor,
                        "site" to call.site.name,
                        "sourceFile" to call.sourceFile,
                        "line" to call.line,
                    )
                }
            ),
        )

    private fun header(result: AmbientResult): String {
        val classes = result.all.map { it.callerClass.topLevelClass() }.toSet().size
        val subject = when (result.subjectSource) {
            SubjectSource.RINGS -> "ring-0"
            SubjectSource.DOMAIN_PATTERN -> "matched"
        }
        return "Ambient input in the domain: ${result.all.size} finding(s) in $classes of ${result.domainClassCount} $subject class(es)"
    }

    private fun conventionLine(result: AmbientResult): String = when (result.clockConvention) {
        ClockConvention.INJECTED ->
            "Clock convention: ${result.clockInjectingClasses.size} class(es) inject a Clock — clock findings below are drift from the project's own practice"
        ClockConvention.ABSENT ->
            "Clock convention: no class injects a Clock anywhere — clock findings below are advisory (a design choice, not drift); other categories stand on their own"
    }

    private fun callLine(call: AmbientCall): String =
        "${call.callerClass.simpleName()}.${call.callerMethod}  ${call.signal.label}  ${call.location()}"
}
