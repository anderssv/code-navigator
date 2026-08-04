package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject
import no.f12.codenavigator.formatting.jsonStringArray
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

object DsmFormatter {

    fun noResultsHints(packageCount: Int): List<String> = buildList {
        if (packageCount <= 1) {
            add("All classes are in a single package. The DSM shows inter-package dependencies, so there is nothing to display. Consider splitting classes into multiple packages.")
        }
    }

    fun format(matrix: DsmMatrix, moduleLabels: Map<PackageName, Set<String>> = emptyMap()): String {
        val packages = matrix.packages
        if (packages.isEmpty()) return "No inter-package dependencies found."
        val prefix = matrix.displayPrefix

        return buildString {
            if (prefix.isNotEmpty()) {
                appendLine("Common prefix: $prefix (stripped for readability)")
                appendLine()
            }
            appendLine("=== Dependency Structure Matrix (DSM) ===")
            appendLine()
            appendLine("Legend:")
            packages.forEachIndexed { i, pkg ->
                appendLine("  ${(i + 1).toString().padStart(3)}: ${labelFor(pkg, moduleLabels)}")
            }
            appendLine()

            appendLine("Reading: row depends on column. Cell value = number of dependency references.")
            appendLine("         Cells below the diagonal indicate forward dependencies (good).")
            appendLine("         Cells above the diagonal indicate backward/cyclic dependencies (review these).")
            appendLine()

            val colWidth = maxOf(packages.size.toString().length, 4)
            val labelWidth = packages.maxOf { labelFor(it, moduleLabels).length }.coerceAtLeast(10)

            append("".padEnd(labelWidth + 6))
            packages.forEachIndexed { i, _ ->
                append((i + 1).toString().padStart(colWidth))
            }
            appendLine()

            val totalWidth = labelWidth + 6 + packages.size * colWidth
            appendLine("-".repeat(totalWidth))

            packages.forEachIndexed { rowIdx, rowPkg ->
                append("${(rowIdx + 1).toString().padStart(3)}. ${labelFor(rowPkg, moduleLabels).padEnd(labelWidth)}")
                packages.forEachIndexed { colIdx, colPkg ->
                    val cell = when {
                        rowIdx == colIdx -> "."
                        else -> matrix.cells[rowPkg to colPkg]?.toString() ?: ""
                    }
                    append(cell.padStart(colWidth))
                }
                appendLine()
            }

            val cyclicPairs = matrix.findCyclicPairs()
            if (cyclicPairs.isNotEmpty()) {
                appendLine()
                appendLine("WARNING: Cyclic dependencies detected:")
                cyclicPairs.forEach { (a, b, counts) ->
                    appendLine("  $a <-> $b  (${counts.first} refs / ${counts.second} refs)")
                    val fwd = matrix.classDependencies[a to b]
                    val bwd = matrix.classDependencies[b to a]
                    fwd?.take(5)?.forEach { (src, tgt) -> appendLine("    ${src.stripPackagePrefix(prefix)} -> ${tgt.stripPackagePrefix(prefix)}") }
                    bwd?.take(5)?.forEach { (src, tgt) -> appendLine("    ${src.stripPackagePrefix(prefix)} -> ${tgt.stripPackagePrefix(prefix)}") }
                }
            }
        }.trimEnd()
    }

    fun formatCycles(matrix: DsmMatrix, cycleFilter: Pair<PackageName, PackageName>? = null): String {
        val cyclicPairs = matrix.findCyclicPairs(cycleFilter)
        if (cyclicPairs.isEmpty()) return "No cyclic dependencies found."
        val prefix = matrix.displayPrefix

        return buildString {
            if (prefix.isNotEmpty()) {
                appendLine("Common prefix: $prefix (stripped for readability)")
                appendLine()
            }
            cyclicPairs.forEachIndexed { idx, (a, b, counts) ->
                if (idx > 0) appendLine()
                val fwdLabel = if (counts.first == 1) "ref" else "refs"
                val bwdLabel = if (counts.second == 1) "ref" else "refs"
                appendLine("CYCLE: $a <-> $b (${counts.first} $fwdLabel / ${counts.second} $bwdLabel)")
                appendLine("  $a -> $b:")
                val fwd = matrix.classDependencies[a to b]
                fwd?.sortedBy { "${it.first}-${it.second}" }?.forEach { (src, tgt) ->
                    appendLine("    ${src.stripPackagePrefix(prefix)} -> ${tgt.stripPackagePrefix(prefix)}")
                }
                appendLine("  $b -> $a:")
                val bwd = matrix.classDependencies[b to a]
                bwd?.sortedBy { "${it.first}-${it.second}" }?.forEach { (src, tgt) ->
                    appendLine("    ${src.stripPackagePrefix(prefix)} -> ${tgt.stripPackagePrefix(prefix)}")
                }
            }
        }.trimEnd()
    }

    /** Renders "[:module] pkg" when the workspace contains multiple modules; plain "pkg" otherwise. */
    internal fun labelFor(pkg: PackageName, moduleLabels: Map<PackageName, Set<String>>): String {
        val modules = moduleLabels[pkg]
        if (modules.isNullOrEmpty()) return pkg.toString()
        return "[${modules.sorted().joinToString(",")}] $pkg"
    }

    fun formatJson(matrix: DsmMatrix, moduleLabels: Map<PackageName, Set<String>> = emptyMap()): String {
        val prefix = matrix.displayPrefix
        val packages = jsonStringArray(matrix.packages.map { it.toString() })
        val cells = jsonArray(matrix.cells.entries.toList().sortedBy { "${it.key.first}-${it.key.second}" }) { (key, count) ->
            val classDeps = matrix.classDependencies[key]
            jsonObject(
                "from" to key.first.toString(),
                "to" to key.second.toString(),
                "count" to count,
                "classes" to JsonRaw(
                    jsonArray(classDeps?.toList()?.sortedBy { "${it.first}-${it.second}" } ?: emptyList()) { (src, tgt) ->
                        jsonObject("source" to src.stripPackagePrefix(prefix).toString(), "target" to tgt.stripPackagePrefix(prefix).toString())
                    },
                ),
            )
        }
        val cycles = matrix.findCyclicPairs()
        val cyclesJson = jsonArray(cycles) { (a, b, counts) ->
            jsonObject("packageA" to a.toString(), "packageB" to b.toString(), "forwardRefs" to counts.first, "backwardRefs" to counts.second)
        }
        val prefixStr = if (prefix.isNotEmpty()) prefix.toString() else null
        val packageModules = if (moduleLabels.isEmpty()) {
            null
        } else {
            JsonRaw(
                jsonArray(matrix.packages) { pkg ->
                    jsonObject(
                        "package" to pkg.toString(),
                        "modules" to JsonRaw(jsonStringArray((moduleLabels[pkg] ?: emptySet()).sorted())),
                    )
                },
            )
        }
        return jsonObject(
            "displayPrefix" to prefixStr,
            "packages" to JsonRaw(packages),
            "packageModules" to packageModules,
            "cells" to JsonRaw(cells),
            "cycles" to JsonRaw(cyclesJson),
        )
    }

    fun formatCyclesJson(matrix: DsmMatrix, cycleFilter: Pair<PackageName, PackageName>? = null): String {
        val cycles = matrix.findCyclicPairs(cycleFilter)
        val prefix = matrix.displayPrefix
        val cyclesJson = jsonArray(cycles) { (a, b, counts) ->
            val fwdEdges = matrix.classDependencies[a to b]
            val bwdEdges = matrix.classDependencies[b to a]
            jsonObject(
                "packageA" to a.toString(),
                "packageB" to b.toString(),
                "forwardRefs" to counts.first,
                "backwardRefs" to counts.second,
                "forwardEdges" to JsonRaw(
                    jsonArray(fwdEdges?.toList()?.sortedBy { "${it.first}-${it.second}" } ?: emptyList()) { (src, tgt) ->
                        jsonObject("source" to src.stripPackagePrefix(prefix).toString(), "target" to tgt.stripPackagePrefix(prefix).toString())
                    },
                ),
                "backwardEdges" to JsonRaw(
                    jsonArray(bwdEdges?.toList()?.sortedBy { "${it.first}-${it.second}" } ?: emptyList()) { (src, tgt) ->
                        jsonObject("source" to src.stripPackagePrefix(prefix).toString(), "target" to tgt.stripPackagePrefix(prefix).toString())
                    },
                ),
            )
        }
        val prefixStr = if (prefix.isNotEmpty()) prefix.toString() else null
        return jsonObject("displayPrefix" to prefixStr, "cycles" to JsonRaw(cyclesJson))
    }

    fun formatLlm(matrix: DsmMatrix, moduleLabels: Map<PackageName, Set<String>> = emptyMap()): String = buildString {
        val prefix = matrix.displayPrefix
        if (prefix.isNotEmpty()) {
            appendLine("prefix:$prefix")
        }
        append("packages:${matrix.packages.joinToString(",") { labelFor(it, moduleLabels) }}")
        if (matrix.cells.isEmpty()) {
            append("\n(no dependencies)")
        } else {
            for ((key, count) in matrix.cells.entries.sortedBy { "${it.key.first}-${it.key.second}" }) {
                append("\n${key.first}->${key.second}:$count")
                val classDeps = matrix.classDependencies[key]
                if (!classDeps.isNullOrEmpty()) {
                    val classStr = classDeps.sortedBy { "${it.first}-${it.second}" }
                        .joinToString(",") { "${it.first.stripPackagePrefix(prefix)}->${it.second.stripPackagePrefix(prefix)}" }
                    append(" [$classStr]")
                }
            }
            val cyclicPairs = matrix.findCyclicPairs()
            if (cyclicPairs.isNotEmpty()) {
                val cycleStr = cyclicPairs.joinToString(",") { (a, b, _) -> "$a<->$b" }
                append("\nCYCLES: $cycleStr")
            }
        }
    }

    fun formatCyclesLlm(matrix: DsmMatrix, cycleFilter: Pair<PackageName, PackageName>? = null): String {
        val cyclicPairs = matrix.findCyclicPairs(cycleFilter)
        if (cyclicPairs.isEmpty()) return "(no cycles)"
        val prefix = matrix.displayPrefix

        return buildString {
            if (prefix.isNotEmpty()) {
                appendLine("prefix:$prefix")
            }
            append(cyclicPairs.joinToString("\n") { (a, b, counts) ->
                val fwd = matrix.classDependencies[a to b]
                val bwd = matrix.classDependencies[b to a]
                val fwdStr = fwd?.sortedBy { "${it.first}-${it.second}" }
                    ?.joinToString(",") { "${it.first.stripPackagePrefix(prefix)}->${it.second.stripPackagePrefix(prefix)}" } ?: ""
                val bwdStr = bwd?.sortedBy { "${it.first}-${it.second}" }
                    ?.joinToString(",") { "${it.first.stripPackagePrefix(prefix)}->${it.second.stripPackagePrefix(prefix)}" } ?: ""
                buildString {
                    append("CYCLE $a<->$b ${counts.first}/${counts.second}")
                    if (fwdStr.isNotEmpty()) append("\n  $a->$b: $fwdStr")
                    if (bwdStr.isNotEmpty()) append("\n  $b->$a: $bwdStr")
                }
            })
        }
    }
}
