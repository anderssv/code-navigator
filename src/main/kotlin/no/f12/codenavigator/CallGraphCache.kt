package no.f12.codenavigator

import java.io.File

object CallGraphCache {

    private const val FIELD_SEPARATOR = "\t"
    private const val EDGES_HEADER = "[EDGES]"
    private const val SOURCES_HEADER = "[SOURCES]"

    fun write(cacheFile: File, graph: CallGraph) {
        CacheFreshness.atomicWrite(cacheFile) { file ->
            file.bufferedWriter().use { writer ->
                writer.write(EDGES_HEADER)
                writer.newLine()
                graph.forEachEdge { caller, callee ->
                    writer.write(
                        listOf(
                            caller.className,
                            caller.methodName,
                            callee.className,
                            callee.methodName,
                        ).joinToString(FIELD_SEPARATOR),
                    )
                    writer.newLine()
                }
                writer.write(SOURCES_HEADER)
                writer.newLine()
                graph.forEachSourceFile { className, sourceFile ->
                    writer.write(
                        listOf(className, sourceFile).joinToString(FIELD_SEPARATOR),
                    )
                    writer.newLine()
                }
            }
        }
    }

    fun read(cacheFile: File): CallGraph {
        val callerToCallees = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        val sourceFiles = mutableMapOf<String, String>()

        var section = ""
        cacheFile.useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                when {
                    line == EDGES_HEADER -> section = EDGES_HEADER
                    line == SOURCES_HEADER -> section = SOURCES_HEADER
                    section == EDGES_HEADER -> {
                        val parts = line.split(FIELD_SEPARATOR)
                        val caller = MethodRef(parts[0], parts[1])
                        val callee = MethodRef(parts[2], parts[3])
                        callerToCallees.getOrPut(caller) { mutableSetOf() }.add(callee)
                    }
                    section == SOURCES_HEADER -> {
                        val parts = line.split(FIELD_SEPARATOR)
                        sourceFiles[parts[0]] = parts[1]
                    }
                }
            }
        }

        return CallGraph(callerToCallees, sourceFiles)
    }

    fun isFresh(cacheFile: File, classDirectories: List<File>): Boolean =
        CacheFreshness.isFresh(cacheFile, classDirectories)

    fun getOrBuild(cacheFile: File, classDirectories: List<File>): CallGraph {
        if (isFresh(cacheFile, classDirectories)) {
            try {
                return read(cacheFile)
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }

        val graph = CallGraphBuilder.build(classDirectories)
        write(cacheFile, graph)
        return graph
    }
}
