package no.f12.codenavigator

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.FileAge
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.analysis.ModuleAuthors
import no.f12.codenavigator.navigation.callgraph.AnnotationTag
import no.f12.codenavigator.navigation.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.classinfo.AnnotationDetail
import no.f12.codenavigator.navigation.classinfo.ClassDetail
import no.f12.codenavigator.navigation.classinfo.ClassInfo
import no.f12.codenavigator.navigation.interfaces.InterfaceRegistry
import no.f12.codenavigator.navigation.dsm.PackageDependencies
import no.f12.codenavigator.navigation.PackageName
import no.f12.codenavigator.navigation.symbol.SymbolInfo
import no.f12.codenavigator.navigation.dsm.DsmMatrix
import no.f12.codenavigator.navigation.rank.RankedType
import no.f12.codenavigator.navigation.complexity.ClassComplexity
import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.deadcode.DeadCode
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.stringconstant.StringConstantMatch
import no.f12.codenavigator.navigation.hierarchy.SupertypeInfo
import no.f12.codenavigator.navigation.hierarchy.TypeHierarchyResult
import no.f12.codenavigator.navigation.callgraph.UsageSite
import no.f12.codenavigator.navigation.annotation.AnnotationMatch

@JvmInline
private value class JsonRaw(val json: String)

object JsonFormatter {

    fun formatClasses(classes: List<ClassInfo>): String =
        jsonArray(classes.sortedBy { it.className }) { c ->
            jsonObject(
                "className" to c.className.displayName(),
                "sourceFile" to c.sourceFileName,
                "sourcePath" to c.reconstructedSourcePath,
            )
        }

    fun formatSymbols(symbols: List<SymbolInfo>): String =
        jsonArray(symbols.sortedWith(compareBy({ it.packageName.toString() }, { it.className.toString() }, { it.symbolName }))) { s ->
            jsonObject(
                "package" to s.packageName.toString(),
                "class" to s.className.simpleName(),
                "symbol" to s.symbolName,
                "kind" to s.kind.name.lowercase(),
                "sourceFile" to s.sourceFile,
            )
        }

    fun formatClassDetails(details: List<ClassDetail>): String =
        jsonArray(details.sortedBy { it.className }) { d ->
            jsonObject(
                "className" to d.className.toString(),
                "sourceFile" to d.sourceFile,
                "superClass" to d.superClass?.toString(),
                "annotations" to if (d.annotations.isNotEmpty()) JsonRaw(renderAnnotations(d.annotations)) else null,
                "interfaces" to JsonRaw(jsonStringArray(d.interfaces.map { it.toString() })),
                "fields" to JsonRaw(jsonArray(d.fields) { f ->
                    jsonObject(
                        "name" to f.name,
                        "type" to f.type,
                        "annotations" to if (f.annotations.isNotEmpty()) JsonRaw(renderAnnotations(f.annotations)) else null,
                    )
                }),
                "methods" to JsonRaw(jsonArray(d.methods) { m ->
                    jsonObject(
                        "name" to m.name,
                        "parameters" to JsonRaw(jsonStringArray(m.parameterTypes)),
                        "returnType" to m.returnType,
                        "annotations" to if (m.annotations.isNotEmpty()) JsonRaw(renderAnnotations(m.annotations)) else null,
                    )
                }),
            )
        }

    fun renderCallTrees(trees: List<CallTreeNode>): String =
        jsonArray(trees) { node -> renderCallNode(node) }

    fun formatInterfaces(registry: InterfaceRegistry, interfaceNames: List<ClassName>): String =
        jsonArray(interfaceNames.sorted()) { name ->
            val implementors = registry.implementorsOf(name)
            jsonObject(
                "interface" to name.toString(),
                "implementors" to JsonRaw(jsonArray(implementors.sortedBy { it.className }) { impl ->
                    jsonObject("className" to impl.className.toString(), "sourceFile" to impl.sourceFile)
                }),
            )
        }

    fun formatTypeHierarchy(results: List<TypeHierarchyResult>): String =
        jsonArray(results.sortedBy { it.className }) { result ->
            jsonObject(
                "className" to result.className.toString(),
                "sourceFile" to result.sourceFile,
                "supertypes" to JsonRaw(renderSupertypes(result.supertypes)),
                "implementors" to JsonRaw(jsonArray(result.implementors.sortedBy { it.className }) { impl ->
                    jsonObject("className" to impl.className.toString(), "sourceFile" to impl.sourceFile)
                }),
            )
        }

    fun formatPackageDeps(
        deps: PackageDependencies,
        packageNames: List<PackageName>,
        reverse: Boolean = false,
    ): String =
        jsonArray(packageNames) { pkg ->
            val related = if (reverse) deps.dependentsOf(pkg) else deps.dependenciesOf(pkg)
            val key = if (reverse) "dependents" else "dependencies"
            jsonObject(
                "package" to pkg.toString(),
                key to JsonRaw(jsonStringArray(related.map { it.toString() })),
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
        val packages = jsonStringArray(matrix.packages.map { it.toString() })
        val cells = jsonArray(matrix.cells.entries.toList().sortedBy { "${it.key.first}-${it.key.second}" }) { (key, count) ->
            val classDeps = matrix.classDependencies[key]
            jsonObject(
                "from" to key.first.toString(),
                "to" to key.second.toString(),
                "count" to count,
                "classes" to JsonRaw(
                    jsonArray(classDeps?.toList()?.sortedBy { "${it.first}-${it.second}" } ?: emptyList()) { (src, tgt) ->
                        jsonObject("source" to src.toString(), "target" to tgt.toString())
                    },
                ),
            )
        }
        val cycles = matrix.findCyclicPairs()
        val cyclesJson = jsonArray(cycles) { (a, b, counts) ->
            jsonObject("packageA" to a.toString(), "packageB" to b.toString(), "forwardRefs" to counts.first, "backwardRefs" to counts.second)
        }
        return jsonObject("packages" to JsonRaw(packages), "cells" to JsonRaw(cells), "cycles" to JsonRaw(cyclesJson))
    }

    fun formatDsmCycles(matrix: DsmMatrix, cycleFilter: Pair<PackageName, PackageName>? = null): String {
        val cycles = matrix.findCyclicPairs(cycleFilter)
        return jsonArray(cycles) { (a, b, counts) ->
            val fwdEdges = matrix.classDependencies[a to b]
            val bwdEdges = matrix.classDependencies[b to a]
            jsonObject(
                "packageA" to a.toString(),
                "packageB" to b.toString(),
                "forwardRefs" to counts.first,
                "backwardRefs" to counts.second,
                "forwardEdges" to JsonRaw(
                    jsonArray(fwdEdges?.toList()?.sortedBy { "${it.first}-${it.second}" } ?: emptyList()) { (src, tgt) ->
                        jsonObject("source" to src.toString(), "target" to tgt.toString())
                    },
                ),
                "backwardEdges" to JsonRaw(
                    jsonArray(bwdEdges?.toList()?.sortedBy { "${it.first}-${it.second}" } ?: emptyList()) { (src, tgt) ->
                        jsonObject("source" to src.toString(), "target" to tgt.toString())
                    },
                ),
            )
        }
    }

    fun formatUsages(usages: List<UsageSite>): String =
        jsonArray(usages.sortedWith(compareBy({ it.callerClass }, { it.callerMethod }))) { u ->
            jsonObject(
                "callerClass" to u.callerClass.toString(),
                "callerMethod" to u.callerMethod,
                "sourceFile" to u.sourceFile,
                "targetOwner" to u.targetOwner.toString(),
                "targetMethod" to u.targetName,
                "targetDescriptor" to u.targetDescriptor,
                "kind" to u.kind.name.lowercase(),
            )
        }

    fun formatRank(ranked: List<RankedType>): String =
        jsonArray(ranked) { r ->
            jsonObject(
                "className" to r.className.toString(),
                "rank" to r.rank,
                "inDegree" to r.inDegree,
                "outDegree" to r.outDegree,
            )
        }

    fun formatDead(dead: List<DeadCode>): String =
        jsonArray(dead) { d ->
            jsonObject(
                "className" to d.className.toString(),
                "memberName" to d.memberName,
                "kind" to d.kind.name.lowercase(),
                "sourceFile" to d.sourceFile,
                "confidence" to d.confidence.name.lowercase(),
                "reason" to d.reason.name.lowercase(),
            )
        }

    fun formatStringConstants(matches: List<StringConstantMatch>): String =
        jsonArray(matches) { m ->
            jsonObject(
                "className" to m.className.toString(),
                "methodName" to m.methodName,
                "value" to m.value,
                "sourceFile" to m.sourceFile,
            )
        }

    fun formatAnnotations(matches: List<AnnotationMatch>): String =
        jsonArray(matches) { match ->
            jsonObject(
                "className" to match.className.value,
                "sourceFile" to match.sourceFile,
                "classAnnotations" to JsonRaw(jsonStringArray(match.classAnnotations.sorted().map { it.value })),
                "methods" to JsonRaw(jsonArray(match.matchedMethods) { method ->
                    jsonObject(
                        "method" to method.method.methodName,
                        "annotations" to JsonRaw(jsonStringArray(method.annotations.sorted().map { it.value })),
                    )
                }),
            )
        }

    fun formatComplexity(results: List<ClassComplexity>): String =
        jsonArray(results) { c ->
            jsonObject(
                "className" to c.className.toString(),
                "sourceFile" to c.sourceFile,
                "fanOut" to c.fanOut,
                "fanIn" to c.fanIn,
                "distinctOutgoingClasses" to c.distinctOutgoingClasses,
                "distinctIncomingClasses" to c.distinctIncomingClasses,
                "outgoingByClass" to JsonRaw(jsonArray(c.outgoingByClass) { (cls, count) ->
                    jsonObject("className" to cls.toString(), "count" to count)
                }),
                "incomingByClass" to JsonRaw(jsonArray(c.incomingByClass) { (cls, count) ->
                    jsonObject("className" to cls.toString(), "count" to count)
                }),
            )
        }

    fun formatCycles(details: List<CycleDetail>): String =
        jsonArray(details) { detail ->
            jsonObject(
                "packages" to JsonRaw(jsonStringArray(detail.packages.map { it.toString() })),
                "edges" to JsonRaw(jsonArray(detail.edges) { edge ->
                    jsonObject(
                        "from" to edge.from.toString(),
                        "to" to edge.to.toString(),
                        "classEdges" to JsonRaw(
                            jsonArray(edge.classEdges.toList().sortedBy { "${it.first}-${it.second}" }) { (src, tgt) ->
                                jsonObject("source" to src.toString(), "target" to tgt.toString())
                            },
                        ),
                    )
                }),
            )
        }

    fun formatMetrics(metrics: MetricsResult): String =
        jsonObject(
            "totalClasses" to metrics.totalClasses,
            "packageCount" to metrics.packageCount,
            "averageFanIn" to metrics.averageFanIn,
            "averageFanOut" to metrics.averageFanOut,
            "cycleCount" to metrics.cycleCount,
            "deadClassCount" to metrics.deadClassCount,
            "deadMethodCount" to metrics.deadMethodCount,
            "topHotspots" to JsonRaw(jsonArray(metrics.topHotspots) { h ->
                jsonObject(
                    "file" to h.file,
                    "revisions" to h.revisions,
                    "totalChurn" to h.totalChurn,
                )
            }),
        )

    private fun renderCallNode(node: CallTreeNode): String {
        val children = jsonArray(node.children) { child -> renderCallNode(child) }
        return jsonObject(
            "method" to node.method.qualifiedName,
            "sourceFile" to node.sourceFile,
            "lineNumber" to node.lineNumber,
            "annotations" to if (node.annotations.isNotEmpty()) JsonRaw(renderAnnotationTags(node.annotations)) else null,
            "children" to JsonRaw(children),
        )
    }

    private fun renderAnnotationTags(tags: List<AnnotationTag>): String =
        tags.joinToString(",", "[", "]") { tag ->
            val params = if (tag.parameters.isNotEmpty()) {
                JsonRaw(jsonObject(*tag.parameters.map { (k, v) -> k to v }.toTypedArray()))
            } else {
                null
            }
            jsonObject(
                "name" to tag.name.value,
                "framework" to tag.framework,
                "parameters" to params,
            )
        }

    private fun renderSupertypes(supertypes: List<SupertypeInfo>): String =
        jsonArray(supertypes) { st ->
            jsonObject(
                "className" to st.className.toString(),
                "kind" to st.kind.name.lowercase(),
                "supertypes" to JsonRaw(renderSupertypes(st.supertypes)),
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

    private fun renderAnnotations(annotations: List<AnnotationDetail>): String =
        jsonArray(annotations) { a ->
            jsonObject(
                "name" to a.name.value,
                "parameters" to JsonRaw(jsonObject(*a.parameters.map { (k, v) -> k to v }.toTypedArray())),
            )
        }
}
