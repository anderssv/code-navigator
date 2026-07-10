package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.withInterpretation

object PackageDistanceFormatter {

    internal const val DISTANCE_INTERPRETATION = "Interpretation: Distance measures package name segment separation (e.g., com.a.b → com.x.y = distance 4). High distance + high dependency count suggests coupling between unrelated parts of the codebase that may benefit from an intermediate abstraction."

    fun noResultsHints(packageCount: Int): List<String> = buildList {
        if (packageCount <= 1) {
            add("All classes are in a single package. Package distance measures inter-package relationships, so there is nothing to display.")
        }
    }

    fun format(result: PackageDistanceResult): String {
        if (result.entries.isEmpty()) return "No inter-package dependencies found."

        return buildString {
            if (result.displayPrefix.isNotEmpty()) {
                appendLine("Common prefix: ${result.displayPrefix} (stripped for readability)")
                appendLine()
            }
            append(result.entries.joinToString("\n") { entry ->
                "${entry.source} → ${entry.target}  distance=${entry.distance}  deps=${entry.dependencyCount}"
            })
        }.trimEnd()
    }

    fun formatJson(result: PackageDistanceResult): String {
        val entriesJson = jsonArray(result.entries) { entry ->
            jsonObject(
                "source" to entry.source.toString(),
                "target" to entry.target.toString(),
                "distance" to entry.distance,
                "deps" to entry.dependencyCount,
            )
        }
        val prefix = if (result.displayPrefix.isNotEmpty()) result.displayPrefix.toString() else null
        return jsonObject("displayPrefix" to prefix, "entries" to JsonRaw(entriesJson))
    }

    fun formatLlm(result: PackageDistanceResult): String {
        if (result.entries.isEmpty()) return ""
        return buildString {
            if (result.displayPrefix.isNotEmpty()) {
                appendLine("prefix:${result.displayPrefix}")
            }
            append(result.entries.joinToString("\n") { entry ->
                "${entry.source}->${entry.target} distance=${entry.distance} deps=${entry.dependencyCount}"
            })
        }.withInterpretation(DISTANCE_INTERPRETATION)
    }
}
