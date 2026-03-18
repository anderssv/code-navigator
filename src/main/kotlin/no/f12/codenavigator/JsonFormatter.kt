package no.f12.codenavigator

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
