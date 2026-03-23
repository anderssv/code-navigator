package no.f12.codenavigator

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.FileAge
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.analysis.ModuleAuthors
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallGraph
import no.f12.codenavigator.navigation.CallTreeBuilder
import no.f12.codenavigator.navigation.CallTreeNode
import no.f12.codenavigator.navigation.ClassDetail
import no.f12.codenavigator.navigation.ClassInfo
import no.f12.codenavigator.navigation.InterfaceRegistry
import no.f12.codenavigator.navigation.MethodRef
import no.f12.codenavigator.navigation.PackageDependencies
import no.f12.codenavigator.navigation.SymbolInfo
import no.f12.codenavigator.navigation.DsmMatrix

@JvmInline
private value class JsonRaw(val json: String)

object JsonFormatter {

    fun formatClasses(classes: List<ClassInfo>): String =
        jsonArray(classes.sortedBy { it.className }) { c ->
            jsonObject(
                "className" to c.className,
                "sourceFile" to c.sourceFileName,
                "sourcePath" to c.reconstructedSourcePath,
            )
        }

    fun formatSymbols(symbols: List<SymbolInfo>): String =
        jsonArray(symbols.sortedWith(compareBy({ it.packageName }, { it.className }, { it.symbolName }))) { s ->
            jsonObject(
                "package" to s.packageName,
                "class" to s.className,
                "symbol" to s.symbolName,
                "kind" to s.kind.name.lowercase(),
                "sourceFile" to s.sourceFile,
            )
        }

    fun formatClassDetails(details: List<ClassDetail>): String =
        jsonArray(details.sortedBy { it.className }) { d ->
            jsonObject(
                "className" to d.className,
                "sourceFile" to d.sourceFile,
                "superClass" to d.superClass,
                "interfaces" to JsonRaw(jsonStringArray(d.interfaces)),
                "fields" to JsonRaw(jsonArray(d.fields) { f ->
                    jsonObject("name" to f.name, "type" to f.type)
                }),
                "methods" to JsonRaw(jsonArray(d.methods) { m ->
                    jsonObject(
                        "name" to m.name,
                        "parameters" to JsonRaw(jsonStringArray(m.parameterTypes)),
                        "returnType" to m.returnType,
                    )
                }),
            )
        }

    fun formatCallTree(
        graph: CallGraph,
        methods: List<MethodRef>,
        maxDepth: Int,
        direction: CallDirection,
        filter: ((MethodRef) -> Boolean)? = null,
    ): String {
        val trees = CallTreeBuilder.build(graph, methods, maxDepth, direction, filter)
        return renderCallTrees(trees)
    }

    fun renderCallTrees(trees: List<CallTreeNode>): String =
        jsonArray(trees) { node -> renderCallNode(node) }

    fun formatInterfaces(registry: InterfaceRegistry, interfaceNames: List<String>): String =
        jsonArray(interfaceNames.sorted()) { name ->
            val implementors = registry.implementorsOf(name)
            jsonObject(
                "interface" to name,
                "implementors" to JsonRaw(jsonArray(implementors.sortedBy { it.className }) { impl ->
                    jsonObject("className" to impl.className, "sourceFile" to impl.sourceFile)
                }),
            )
        }

    fun formatPackageDeps(
        deps: PackageDependencies,
        packageNames: List<String>,
        reverse: Boolean = false,
    ): String =
        jsonArray(packageNames) { pkg ->
            val related = if (reverse) deps.dependentsOf(pkg) else deps.dependenciesOf(pkg)
            val key = if (reverse) "dependents" else "dependencies"
            jsonObject(
                "package" to pkg,
                key to JsonRaw(jsonStringArray(related)),
            )
        }

    fun formatHotspots(hotspots: List<Hotspot>): String =
        jsonArray(hotspots) { h ->
            jsonObject(
                "file" to h.file,
                "revisions" to h.revisions,
                "totalChurn" to h.totalChurn,
            )
        }

    fun formatCoupling(pairs: List<CoupledPair>): String =
        jsonArray(pairs) { p ->
            jsonObject(
                "entity" to p.entity,
                "coupled" to p.coupled,
                "degree" to p.degree,
                "sharedRevs" to p.sharedRevs,
                "avgRevs" to p.avgRevs,
            )
        }

    fun formatAge(ages: List<FileAge>): String =
        jsonArray(ages) { a ->
            jsonObject(
                "file" to a.file,
                "ageMonths" to a.ageMonths,
                "lastChangeDate" to a.lastChangeDate.toString(),
            )
        }

    fun formatAuthors(modules: List<ModuleAuthors>): String =
        jsonArray(modules) { m ->
            jsonObject(
                "file" to m.file,
                "authors" to m.authors,
                "revisions" to m.revisions,
            )
        }

    fun formatChurn(churn: List<FileChurn>): String =
        jsonArray(churn) { c ->
            jsonObject(
                "file" to c.file,
                "added" to c.added,
                "deleted" to c.deleted,
                "commits" to c.commits,
            )
        }

    fun formatDsm(matrix: DsmMatrix): String {
        val packages = jsonStringArray(matrix.packages)
        val cells = jsonArray(matrix.cells.entries.toList().sortedBy { "${it.key.first}-${it.key.second}" }) { (key, count) ->
            val classDeps = matrix.classDependencies[key]
            jsonObject(
                "from" to key.first,
                "to" to key.second,
                "count" to count,
                "classes" to JsonRaw(
                    jsonArray(classDeps?.toList()?.sortedBy { "${it.first}-${it.second}" } ?: emptyList()) { (src, tgt) ->
                        jsonObject("source" to src, "target" to tgt)
                    },
                ),
            )
        }
        val cycles = matrix.findCyclicPairs()
        val cyclesJson = jsonArray(cycles) { (a, b, counts) ->
            jsonObject("packageA" to a, "packageB" to b, "forwardRefs" to counts.first, "backwardRefs" to counts.second)
        }
        return jsonObject("packages" to JsonRaw(packages), "cells" to JsonRaw(cells), "cycles" to JsonRaw(cyclesJson))
    }

    private fun renderCallNode(node: CallTreeNode): String {
        val children = jsonArray(node.children) { child -> renderCallNode(child) }
        return jsonObject(
            "method" to node.method.qualifiedName,
            "sourceFile" to node.sourceFile,
            "children" to JsonRaw(children),
        )
    }

    private fun <T> jsonArray(items: List<T>, render: (T) -> String): String {
        if (items.isEmpty()) return "[]"
        return items.joinToString(",", "[", "]") { render(it) }
    }

    private fun jsonStringArray(items: List<String>): String {
        if (items.isEmpty()) return "[]"
        return items.joinToString(",", "[", "]") { "\"${escapeJson(it)}\"" }
    }

    private fun jsonObject(vararg pairs: Pair<String, Any?>): String =
        pairs
            .filter { (_, v) -> v != null }
            .joinToString(",", "{", "}") { (k, v) ->
                "\"${escapeJson(k)}\":${jsonValue(v!!)}"
            }

    private fun jsonValue(value: Any): String = when (value) {
        is String -> "\"${escapeJson(value)}\""
        is JsonRaw -> value.json
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> "\"${escapeJson(value.toString())}\""
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
