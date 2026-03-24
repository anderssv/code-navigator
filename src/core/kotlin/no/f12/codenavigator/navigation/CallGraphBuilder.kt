package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

data class MethodRef(
    val className: String,
    val methodName: String,
) {
    val qualifiedName: String get() = "$className.$methodName"
}

class CallGraph(
    private val callerToCallees: Map<MethodRef, Set<MethodRef>>,
    private val sourceFiles: Map<String, String> = emptyMap(),
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

    fun callersOf(className: String, methodName: String): Set<MethodRef> =
        calleeToCallers[MethodRef(className, methodName)] ?: emptySet()

    fun calleesOf(className: String, methodName: String): Set<MethodRef> =
        callerToCallees[MethodRef(className, methodName)] ?: emptySet()

    fun findMethods(pattern: String): List<MethodRef> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return allMethods
            .filter { regex.containsMatchIn(it.qualifiedName) }
            .sortedBy { it.qualifiedName }
    }

    fun sourceFileOf(className: String): String =
        sourceFiles[className] ?: "<unknown>"

    fun projectClasses(): Set<String> = sourceFiles.keys

    fun projectClassFilter(): (MethodRef) -> Boolean {
        val classes = projectClasses()
        return { it.className in classes }
    }

    fun forEachEdge(action: (caller: MethodRef, callee: MethodRef) -> Unit) {
        callerToCallees.forEach { (caller, callees) ->
            callees.forEach { callee -> action(caller, callee) }
        }
    }

    fun forEachSourceFile(action: (className: String, sourceFile: String) -> Unit) {
        sourceFiles.forEach { (className, sourceFile) -> action(className, sourceFile) }
    }
}

object CallGraphBuilder {
    fun build(classDirectories: List<File>): ScanResult<CallGraph> {
        val callerToCallees = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        val sourceFiles = mutableMapOf<String, String>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            extractCalls(classFile, callerToCallees, sourceFiles)
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(
            data = CallGraph(callerToCallees, sourceFiles),
            skippedFiles = skipped,
        )
    }

    private fun extractCalls(
        classFile: File,
        graph: MutableMap<MethodRef, MutableSet<MethodRef>>,
        sourceFiles: MutableMap<String, String>,
    ) {
        val reader = createClassReader(classFile)
        var ownerClassName = ""

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
                    ownerClassName = name.replace('/', '.')
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
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) {
                            val callee = MethodRef(owner.replace('/', '.'), name)
                            graph.getOrPut(caller) { mutableSetOf() }.add(callee)
                        }
                    }
                }
            },
            ClassReader.SKIP_FRAMES,
        )
    }
}
