package no.f12.codenavigator.navigation.callgraph

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.FileCache
import no.f12.codenavigator.navigation.core.ScanResult
import no.f12.codenavigator.navigation.core.SourceSet
import java.io.File

object CallGraphCache : FileCache<CallGraph>() {

    private const val EDGES_HEADER = "[EDGES]"
    private const val SOURCES_HEADER = "[SOURCES]"
    private const val LINES_HEADER = "[LINES]"
    private const val SOURCE_SETS_HEADER = "[SOURCE_SETS]"
    private const val DECLARED_METHODS_HEADER = "[DECLARED_METHODS]"

    override fun write(cacheFile: File, data: CallGraph) {
        writeLines(cacheFile) { writer ->
            writer.write(EDGES_HEADER)
            writer.newLine()
            data.forEachEdge { caller, callee ->
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
            data.forEachSourceFile { className, sourceFile ->
                writer.write(
                    listOf(className.toString(), sourceFile).joinToString(FIELD_SEPARATOR),
                )
                writer.newLine()
            }
            writer.write(LINES_HEADER)
            writer.newLine()
            data.forEachLineNumber { method, lineNumber ->
                writer.write(
                    listOf(method.className.toString(), method.methodName, lineNumber.toString()).joinToString(FIELD_SEPARATOR),
                )
                writer.newLine()
            }
            writer.write(SOURCE_SETS_HEADER)
            writer.newLine()
            data.forEachSourceSet { className, sourceSet ->
                writer.write(
                    listOf(className.toString(), sourceSet.name).joinToString(FIELD_SEPARATOR),
                )
                writer.newLine()
            }
            writer.write(DECLARED_METHODS_HEADER)
            writer.newLine()
            data.forEachDeclaredMethod { className, methods ->
                writer.write(
                    listOf(className.toString(), methods.joinToString(",")).joinToString(FIELD_SEPARATOR),
                )
                writer.newLine()
            }
        }
    }

    override fun read(cacheFile: File): CallGraph {
        val callerToCallees = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        val sourceFiles = mutableMapOf<ClassName, String>()
        val lineNumbers = mutableMapOf<MethodRef, Int>()
        val sourceSets = mutableMapOf<ClassName, SourceSet>()
        val declaredMethods = mutableMapOf<ClassName, Set<String>>()

        var section = ""
        cacheFile.useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                when {
                    line == EDGES_HEADER -> section = EDGES_HEADER
                    line == SOURCES_HEADER -> section = SOURCES_HEADER
                    line == LINES_HEADER -> section = LINES_HEADER
                    line == SOURCE_SETS_HEADER -> section = SOURCE_SETS_HEADER
                    line == DECLARED_METHODS_HEADER -> section = DECLARED_METHODS_HEADER
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
                    section == SOURCE_SETS_HEADER -> {
                        val parts = line.split(FIELD_SEPARATOR)
                        sourceSets[ClassName(parts[0])] = SourceSet.valueOf(parts[1])
                    }
                    section == DECLARED_METHODS_HEADER -> {
                        val parts = line.split(FIELD_SEPARATOR)
                        val methods = parts[1].split(",").filter { it.isNotEmpty() }.toSet()
                        if (methods.isNotEmpty()) {
                            declaredMethods[ClassName(parts[0])] = methods
                        }
                    }
                }
            }
        }

        return CallGraph(callerToCallees, sourceFiles, lineNumbers, sourceSets, declaredMethods)
    }

    override fun build(classDirectories: List<File>): ScanResult<CallGraph> =
        CallGraphBuilder.build(classDirectories)

    fun getOrBuildTagged(
        cacheFile: File,
        taggedDirectories: List<Pair<File, SourceSet>>,
    ): ScanResult<CallGraph> {
        val classDirectories = taggedDirectories.map { it.first }
        if (isFresh(cacheFile, classDirectories)) {
            try {
                return ScanResult(read(cacheFile), emptyList())
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }

        val result = CallGraphBuilder.buildTagged(taggedDirectories)
        write(cacheFile, result.data)
        return result
    }
}
