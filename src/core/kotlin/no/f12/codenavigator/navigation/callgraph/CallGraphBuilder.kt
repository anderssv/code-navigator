package no.f12.codenavigator.navigation.callgraph

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.KotlinMethodFilter
import no.f12.codenavigator.navigation.core.ScanResult
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.core.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.core.createClassReader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

data class MethodRef(
    val className: ClassName,
    val methodName: String,
) {
    val qualifiedName: String get() = "$className.$methodName"

    fun isGenerated(): Boolean = KotlinMethodFilter.isGenerated(methodName)
}

class CallGraph(
    private val callerToCallees: Map<MethodRef, Set<MethodRef>>,
    private val sourceFiles: Map<ClassName, String> = emptyMap(),
    private val lineNumbers: Map<MethodRef, Int> = emptyMap(),
    private val sourceSets: Map<ClassName, SourceSet> = emptyMap(),
    private val declaredMethods: Map<ClassName, Set<String>> = emptyMap(),
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

    fun callersOfClass(className: ClassName): Set<MethodRef> =
        calleeToCallers
            .filter { (callee, _) -> callee.className == className }
            .values
            .flatten()
            .toSet()

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

        val innerClassExpanded = expandInnerClassMethods(pattern)
        if (innerClassExpanded != null) {
            val innerMatches = allMethods
                .filter { innerClassExpanded.containsMatchIn(it.qualifiedName) }
                .sortedBy { it.qualifiedName }
            if (innerMatches.isNotEmpty()) return innerMatches
        }

        val expanded = expandPropertyAccessors(pattern) ?: return emptyList()
        return allMethods
            .filter { expanded.containsMatchIn(it.qualifiedName) }
            .sortedBy { it.qualifiedName }
    }

    private fun expandInnerClassMethods(pattern: String): Regex? {
        val (classPrefix, methodPart) = splitClassAndMethod(pattern) ?: return null
        if (methodPart.isEmpty()) return null
        val innerClassSegment = """(?:\$\w+)*\."""
        return Regex("$classPrefix$innerClassSegment$methodPart", RegexOption.IGNORE_CASE)
    }

    private fun expandPropertyAccessors(pattern: String): Regex? {
        val (classPrefix, methodPart) = splitClassAndMethod(pattern) ?: return null
        if (methodPart.isEmpty()) return null
        val capitalized = methodPart.replaceFirstChar { it.uppercase() }
        val innerClassSegment = """(?:\$\w+)*\."""
        val accessorPattern = "$classPrefix$innerClassSegment(?:get$capitalized|set$capitalized|is$capitalized)"
        return Regex(accessorPattern, RegexOption.IGNORE_CASE)
    }

    private fun splitClassAndMethod(pattern: String): Pair<String, String>? {
        val escapedDotIndex = pattern.lastIndexOf("\\.")
        val plainDotIndex = if (escapedDotIndex < 0) pattern.lastIndexOf('.') else -1
        val (classEnd, methodStart) = when {
            escapedDotIndex >= 0 -> escapedDotIndex to (escapedDotIndex + 2)
            plainDotIndex >= 0 -> plainDotIndex to (plainDotIndex + 1)
            else -> return null
        }
        return pattern.substring(0, classEnd) to pattern.substring(methodStart)
    }

    fun sourceFileOf(className: ClassName): String {
        sourceFiles[className]?.let { return it }
        var current = className
        while (true) {
            val outer = current.outerClass()
            if (outer == current) return "<unknown>"
            sourceFiles[outer]?.let { return it }
            current = outer
        }
    }

    fun projectClasses(): Set<ClassName> = sourceFiles.keys

    fun lineNumberOf(method: MethodRef): Int? = lineNumbers[method]

    fun sourceSetOf(className: ClassName): SourceSet? = sourceSets[className]

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

    fun forEachSourceSet(action: (className: ClassName, sourceSet: SourceSet) -> Unit) {
        sourceSets.forEach { (className, sourceSet) -> action(className, sourceSet) }
    }

    fun declaredMethodsOf(className: ClassName): Set<String> =
        declaredMethods[className] ?: emptySet()

    fun allDeclaredMethods(): Map<ClassName, Set<String>> = declaredMethods

    fun forEachDeclaredMethod(action: (className: ClassName, methods: Set<String>) -> Unit) {
        declaredMethods.forEach { (className, methods) -> action(className, methods) }
    }
}

object CallGraphBuilder {
    fun build(classDirectories: List<File>): ScanResult<CallGraph> =
        buildTagged(classDirectories.map { it to SourceSet.MAIN })

    fun buildTagged(taggedDirectories: List<Pair<File, SourceSet>>): ScanResult<CallGraph> {
        val callerToCallees = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        val sourceFiles = mutableMapOf<ClassName, String>()
        val lineNumbers = mutableMapOf<MethodRef, Int>()
        val sourceSets = mutableMapOf<ClassName, SourceSet>()
        val declaredMethods = mutableMapOf<ClassName, MutableSet<String>>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        taggedDirectories
            .filter { it.first.exists() }
            .forEach { (dir, sourceSet) ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            val classNames = extractCalls(classFile, callerToCallees, sourceFiles, lineNumbers, declaredMethods)
                            classNames.forEach { className -> sourceSets[className] = sourceSet }
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(
            data = CallGraph(callerToCallees, sourceFiles, lineNumbers, sourceSets, declaredMethods),
            skippedFiles = skipped,
        )
    }

    private fun extractCalls(
        classFile: File,
        graph: MutableMap<MethodRef, MutableSet<MethodRef>>,
        sourceFiles: MutableMap<ClassName, String>,
        lineNumbers: MutableMap<MethodRef, Int>,
        declaredMethods: MutableMap<ClassName, MutableSet<String>>,
    ): List<ClassName> {
        val reader = createClassReader(classFile)
        var ownerClassName = ClassName("")
        val discoveredClasses = mutableListOf<ClassName>()

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
                    ownerClassName = ClassName.fromInternal(name)
                    discoveredClasses.add(ownerClassName)
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
                    declaredMethods.getOrPut(ownerClassName) { mutableSetOf() }.add(name)

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
                            val callee = MethodRef(ClassName.fromInternal(owner), name)
                            graph.getOrPut(caller) { mutableSetOf() }.add(callee)
                        }

                        override fun visitInvokeDynamicInsn(
                            name: String,
                            descriptor: String,
                            bootstrapMethodHandle: org.objectweb.asm.Handle,
                            vararg bootstrapMethodArguments: Any,
                        ) {
                            for (arg in bootstrapMethodArguments) {
                                if (arg is org.objectweb.asm.Handle) {
                                    val callee = MethodRef(ClassName.fromInternal(arg.owner), arg.name)
                                    graph.getOrPut(caller) { mutableSetOf() }.add(callee)
                                }
                            }
                        }

                        override fun visitFieldInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                        ) {
                            val fieldOwner = ClassName.fromInternal(owner)
                            if (fieldOwner != ownerClassName) {
                                val syntheticRef = MethodRef(fieldOwner, "<field:$name>")
                                graph.getOrPut(caller) { mutableSetOf() }.add(syntheticRef)
                            }
                        }

                        override fun visitLdcInsn(value: Any?) {
                            if (value is org.objectweb.asm.Type && value.sort == org.objectweb.asm.Type.OBJECT) {
                                val referencedClass = ClassName.fromInternal(value.internalName)
                                if (referencedClass != ownerClassName) {
                                    val syntheticRef = MethodRef(referencedClass, "<class-literal>")
                                    graph.getOrPut(caller) { mutableSetOf() }.add(syntheticRef)
                                }
                            }
                        }

                        override fun visitEnd() {
                            firstLineNumber?.let { lineNumbers[caller] = it }
                        }
                    }
                }
            },
            ClassReader.SKIP_FRAMES,
        )
        return discoveredClasses
    }
}
