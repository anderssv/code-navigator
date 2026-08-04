package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray
import no.f12.codenavigator.formatting.withInterpretation
import no.f12.codenavigator.navigation.types.PackageName

object CyclesFormatter {

    internal const val CYCLES_INTERPRETATION = "Interpretation: Package cycles prevent independent compilation and deployment. To break a cycle, identify the weakest edge (fewest class references) and extract an interface or move the referenced class. Use cnavWhyDepends for edge details."

    fun format(
        details: List<CycleDetail>,
        displayPrefix: PackageName = PackageName(""),
        testInvolvement: TestInvolvement.Counts? = null,
        moduleLabels: Map<PackageName, Set<String>> = emptyMap(),
    ): String {
        if (details.isEmpty()) return "No dependency cycles found."

        return buildString {
            if (displayPrefix.isNotEmpty()) {
                appendLine("Common prefix: $displayPrefix (stripped for readability)")
                appendLine()
            }
            append(details.joinToString("\n\n") { detail ->
                formatCycle(detail, displayPrefix, moduleLabels)
            })
            testInvolvement?.let { counts ->
                TestInvolvement.notice(counts, "cycle edges")?.let { notice ->
                    appendLine()
                    appendLine()
                    append(notice)
                }
            }
        }.trimEnd()
    }

    private fun formatCycle(
        detail: CycleDetail,
        displayPrefix: PackageName,
        moduleLabels: Map<PackageName, Set<String>>,
    ): String = buildString {
        append("CYCLE: ${detail.packages.joinToString(", ") { DsmFormatter.labelFor(it, moduleLabels) }}")

        for (edge in detail.edges) {
            val weight = edge.classEdges.size
            append("\n  ${DsmFormatter.labelFor(edge.from, moduleLabels)} -> ${DsmFormatter.labelFor(edge.to, moduleLabels)} ($weight ref${if (weight != 1) "s" else ""}):")
            for ((src, tgt) in edge.classEdges.sortedBy { "${it.first}-${it.second}" }) {
                append("\n    ${src.stripPackagePrefix(displayPrefix)} -> ${tgt.stripPackagePrefix(displayPrefix)}")
            }
        }

        // Edge ranking
        val ranked = CycleBreakAnalyzer.rankEdges(detail)
        val breakPoints = ranked.filter { it.breaksycle }

        if (breakPoints.isNotEmpty()) {
            append("\n  ⚡ Weakest link${if (breakPoints.size > 1) "s" else ""} to break:")
            for (bp in breakPoints.take(3)) {
                append("\n    ${DsmFormatter.labelFor(bp.from, moduleLabels)} -> ${DsmFormatter.labelFor(bp.to, moduleLabels)} (${bp.weight} ref${if (bp.weight != 1) "s" else ""})")
            }
        }

        append("\n  → Extract shared types into a new package or invert one dependency direction.")
    }

    fun formatJson(
        details: List<CycleDetail>,
        displayPrefix: PackageName = PackageName(""),
        testInvolvement: TestInvolvement.Counts? = null,
        moduleLabels: Map<PackageName, Set<String>> = emptyMap(),
    ): String {
        val cyclesJson = jsonArray(details) { detail ->
            jsonObject(
                "packages" to JsonRaw(jsonStringArray(detail.packages.map { it.toString() })),
                "edges" to JsonRaw(jsonArray(detail.edges) { edge ->
                    jsonObject(
                        "from" to edge.from.toString(),
                        "to" to edge.to.toString(),
                        "classEdges" to JsonRaw(
                            jsonArray(edge.classEdges.toList().sortedBy { "${it.first}-${it.second}" }) { (src, tgt) ->
                                jsonObject("source" to src.stripPackagePrefix(displayPrefix).toString(), "target" to tgt.stripPackagePrefix(displayPrefix).toString())
                            },
                        ),
                    )
                }),
            )
        }
        val prefix = if (displayPrefix.isNotEmpty()) displayPrefix.toString() else null
        val testInvolvementJson = testInvolvement?.let {
            JsonRaw(jsonObject("testInvolved" to it.testInvolved, "total" to it.total))
        }
        val packageModules = if (moduleLabels.isEmpty()) null else JsonRaw(
            jsonArray(moduleLabels.entries.sortedBy { it.key.toString() }) { (pkg, modules) ->
                jsonObject(
                    "package" to pkg.toString(),
                    "modules" to JsonRaw(jsonStringArray(modules.sorted())),
                )
            },
        )
        return jsonObject(
            "displayPrefix" to prefix,
            "cycles" to JsonRaw(cyclesJson),
            "testInvolvement" to testInvolvementJson,
            "packageModules" to packageModules,
        )
    }

    fun formatLlm(
        details: List<CycleDetail>,
        displayPrefix: PackageName = PackageName(""),
        testInvolvement: TestInvolvement.Counts? = null,
        moduleLabels: Map<PackageName, Set<String>> = emptyMap(),
    ): String {
        if (details.isEmpty()) return "(no cycles)"

        return buildString {
            if (displayPrefix.isNotEmpty()) {
                appendLine("prefix:$displayPrefix")
            }
            append(details.joinToString("\n") { detail ->
                buildString {
                    append("CYCLE ${detail.packages.joinToString(",") { DsmFormatter.labelFor(it, moduleLabels) }}")
                    for (edge in detail.edges) {
                        val classStr = edge.classEdges.sortedBy { "${it.first}-${it.second}" }
                            .joinToString(",") { "${it.first.stripPackagePrefix(displayPrefix)}->${it.second.stripPackagePrefix(displayPrefix)}" }
                        append("\n  ${DsmFormatter.labelFor(edge.from, moduleLabels)}->${DsmFormatter.labelFor(edge.to, moduleLabels)}(${edge.classEdges.size}): $classStr")
                    }
                    val ranked = CycleBreakAnalyzer.rankEdges(detail)
                    val breakPoints = ranked.filter { it.breaksycle }.take(3)
                    if (breakPoints.isNotEmpty()) {
                        append("\n  break: ${breakPoints.joinToString(",") { "${DsmFormatter.labelFor(it.from, moduleLabels)}->${DsmFormatter.labelFor(it.to, moduleLabels)}(${it.weight})" }}")
                    }
                }
            })
            testInvolvement?.let { counts ->
                TestInvolvement.notice(counts, "cycle edges")?.let { notice ->
                    appendLine()
                    appendLine()
                    append(notice)
                }
            }
        }.withInterpretation(CYCLES_INTERPRETATION)
    }
}
