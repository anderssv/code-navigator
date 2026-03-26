package no.f12.codenavigator.navigation

import no.f12.codenavigator.CacheFreshness

import java.io.File

object CallGraphCache {

    private const val FIELD_SEPARATOR = "\t"
    private const val EDGES_HEADER = "[EDGES]"
    private const val SOURCES_HEADER = "[SOURCES]"
    private const val LINES_HEADER = "[LINES]"

    fun write(cacheFile: File, graph: CallGraph) {
        CacheFreshness.atomicWrite(cacheFile) { file ->
            file.bufferedWriter().use { writer ->
                writer.write(EDGES_HEADER)
                writer.newLine()
                graph.forEachEdge { caller, callee ->
                    writer.write(
                        listOf(
                            caller.className.toString(),
                            caller.methodName,
                            callee.className.toString(),
                            callee.methodName,
                        ).joinToString(FIELD_SEPARATOR),
                    )
                    writer.newLine()
                }
                writer.write(SOURCES_HEADER)
                writer.newLine()
                graph.forEachSourceFile { className, sourceFile ->
                    writer.write(
                        listOf(className.toString(), sourceFile).joinToString(FIELD_SEPARATOR),
                    )
                    writer.newLine()
                }
                writer.write(LINES_HEADER)
                writer.newLine()
                graph.forEachLineNumber { method, lineNumber ->
                    writer.write(
                        listOf(method.className.toString(), method.methodName, lineNumber.toString()).joinToString(FIELD_SEPARATOR),
                    )
                    writer.newLine()
                }
            }
        }
    }

    fun read(cacheFile: File): CallGraph {
        val callerToCallees = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        val sourceFiles = mutableMapOf<ClassName, String>()
        val lineNumbers = mutableMapOf<MethodRef, Int>()

        var section = ""
        cacheFile.useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                when {
                    line == EDGES_HEADER -> section = EDGES_HEADER
                    line == SOURCES_HEADER -> section = SOURCES_HEADER
                    line == LINES_HEADER -> section = LINES_HEADER
                    section == EDGES_HEADER -> {
                        val parts = line.split(FIELD_SEPARATOR)
                        val caller = MethodRef(ClassName(parts[0]), parts[1])
                        val callee = MethodRef(ClassName(parts[2]), parts[3])
                        callerToCallees.getOrPut(caller) { mutableSetOf() }.add(callee)
                    }
                    section == SOURCES_HEADER -> {
                        val parts = line.split(FIELD_SEPARATOR)
                        sourceFiles[ClassName(parts[0])] = parts[1]
                    }
                    section == LINES_HEADER -> {
                        val parts = line.split(FIELD_SEPARATOR)
                        val method = MethodRef(ClassName(parts[0]), parts[1])
                        lineNumbers[method] = parts[2].toInt()
                    }
                }
            }
        }

        return CallGraph(callerToCallees, sourceFiles, lineNumbers)
    }

    fun isFresh(cacheFile: File, classDirectories: List<File>): Boolean =
        CacheFreshness.isFresh(cacheFile, classDirectories)

    fun getOrBuild(cacheFile: File, classDirectories: List<File>): ScanResult<CallGraph> {
        if (isFresh(cacheFile, classDirectories)) {
            try {
                return ScanResult(read(cacheFile), emptyList())
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }

        val result = CallGraphBuilder.build(classDirectories)
        write(cacheFile, result.data)
        return result
    }
}
