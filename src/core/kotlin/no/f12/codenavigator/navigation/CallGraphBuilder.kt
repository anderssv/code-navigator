package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

data class MethodRef(
    val className: ClassName,
    val methodName: String,
) {
    val qualifiedName: String get() = "${className.value}.$methodName"
}

class CallGraph(
    private val callerToCallees: Map<MethodRef, Set<MethodRef>>,
    private val sourceFiles: Map<ClassName, String> = emptyMap(),
    private val lineNumbers: Map<MethodRef, Int> = emptyMap(),
) {
    private val calleeToCallers: Map<MethodRef, Set<MethodRef>> by lazy {
        val inverted = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        callerToCallees.forEach { (caller, callees) ->
            callees.forEach { callee ->
                inverted.getOrPut(callee) { mutableSetOf() }.add(caller)
            }
        }
        inverted
    }

    private val allMethods: Set<MethodRef> by lazy {
        callerToCallees.keys + callerToCallees.values.flatten()
    }

    fun callersOf(className: ClassName, methodName: String): Set<MethodRef> =
        calleeToCallers[MethodRef(className, methodName)] ?: emptySet()

    fun calleesOf(className: ClassName, methodName: String): Set<MethodRef> =
        callerToCallees[MethodRef(className, methodName)] ?: emptySet()

    fun findMethods(pattern: String): List<MethodRef> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val directMatches = allMethods
            .filter { regex.containsMatchIn(it.qualifiedName) }
            .sortedBy { it.qualifiedName }
        if (directMatches.isNotEmpty()) return directMatches

        val expanded = expandPropertyAccessors(pattern) ?: return emptyList()
        return allMethods
            .filter { expanded.containsMatchIn(it.qualifiedName) }
            .sortedBy { it.qualifiedName }
    }

    private fun expandPropertyAccessors(pattern: String): Regex? {
        val escapedDotIndex = pattern.lastIndexOf("\\.")
        val plainDotIndex = if (escapedDotIndex < 0) pattern.lastIndexOf('.') else -1
        val (classEnd, methodStart) = when {
            escapedDotIndex >= 0 -> escapedDotIndex to (escapedDotIndex + 2)
            plainDotIndex >= 0 -> plainDotIndex to (plainDotIndex + 1)
            else -> return null
        }
        val classPrefix = pattern.substring(0, classEnd)
        val methodPart = pattern.substring(methodStart)
        if (methodPart.isEmpty()) return null
        val capitalized = methodPart.replaceFirstChar { it.uppercase() }
        val innerClassSegment = """(?:\$\w+)*\."""
        val accessorPattern = "$classPrefix$innerClassSegment(?:get$capitalized|set$capitalized|is$capitalized)"
        return Regex(accessorPattern, RegexOption.IGNORE_CASE)
    }

    fun sourceFileOf(className: ClassName): String {
        sourceFiles[className]?.let { return it }
        var name = className.value
        while (true) {
            val idx = name.lastIndexOf('$')
            if (idx < 0) return "<unknown>"
            name = name.substring(0, idx)
            sourceFiles[ClassName(name)]?.let { return it }
        }
    }

    fun projectClasses(): Set<ClassName> = sourceFiles.keys

    fun lineNumberOf(method: MethodRef): Int? = lineNumbers[method]

    fun projectClassFilter(): (MethodRef) -> Boolean {
        val classes = projectClasses()
        return { it.className in classes }
    }

    fun forEachEdge(action: (caller: MethodRef, callee: MethodRef) -> Unit) {
        callerToCallees.forEach { (caller, callees) ->
            callees.forEach { callee -> action(caller, callee) }
        }
    }

    fun forEachSourceFile(action: (className: ClassName, sourceFile: String) -> Unit) {
        sourceFiles.forEach { (className, sourceFile) -> action(className, sourceFile) }
    }

    fun forEachLineNumber(action: (method: MethodRef, lineNumber: Int) -> Unit) {
        lineNumbers.forEach { (method, lineNumber) -> action(method, lineNumber) }
    }
}

object CallGraphBuilder {
    fun build(classDirectories: List<File>): ScanResult<CallGraph> {
        val callerToCallees = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        val sourceFiles = mutableMapOf<ClassName, String>()
        val lineNumbers = mutableMapOf<MethodRef, Int>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            extractCalls(classFile, callerToCallees, sourceFiles, lineNumbers)
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(
            data = CallGraph(callerToCallees, sourceFiles, lineNumbers),
            skippedFiles = skipped,
        )
    }

    private fun extractCalls(
        classFile: File,
        graph: MutableMap<MethodRef, MutableSet<MethodRef>>,
        sourceFiles: MutableMap<ClassName, String>,
        lineNumbers: MutableMap<MethodRef, Int>,
    ) {
        val reader = createClassReader(classFile)
        var ownerClassName = ClassName("")

        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?,
                ) {
                    ownerClassName = ClassName(name.replace('/', '.'))
                }

                override fun visitSource(source: String?, debug: String?) {
                    if (source != null) {
                        sourceFiles[ownerClassName] = source
                    }
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val caller = MethodRef(ownerClassName, name)

                    return object : MethodVisitor(Opcodes.ASM9) {
                        private var firstLineNumber: Int? = null

                        override fun visitLineNumber(line: Int, start: org.objectweb.asm.Label) {
                            if (firstLineNumber == null) {
                                firstLineNumber = line
                            }
                        }

                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) {
                            val callee = MethodRef(ClassName(owner.replace('/', '.')), name)
                            graph.getOrPut(caller) { mutableSetOf() }.add(callee)
                        }

                        override fun visitEnd() {
                            firstLineNumber?.let { lineNumbers[caller] = it }
                        }
                    }
                }
            },
            ClassReader.SKIP_FRAMES,
        )
    }
}
