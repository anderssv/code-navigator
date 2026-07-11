package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.navigation.types.ClassName
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

/**
 * Scans compiled bytecode to find:
 * 1. Which source files contain call sites to a specific method
 * 2. Which classes implement/extend a target class (for declaration renaming)
 *
 * This enables precise PSI renaming: bytecode identifies WHICH files to edit,
 * PSI performs the actual text transformation.
 */
object RenameLocationFinder {

    /**
     * Find source files that contain calls to [className].[methodName].
     * Returns a set of source file paths relative to source roots (e.g., "com/example/admin/AdminRoutes.kt").
     */
    fun findCallSiteFiles(
        classesRoots: List<File>,
        className: String,
        methodName: String,
    ): Set<String> {
        val targetInternal = className.replace('.', '/')
        val callSiteFiles = mutableSetOf<String>()

        forEachClassFile(classesRoots) { classFile ->
            val reader = ClassReader(classFile.readBytes())
            var ownerInternal = ""
            var sourceFile: String? = null
            var callsTarget = false

            reader.accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(
                        version: Int, access: Int, name: String,
                        signature: String?, superName: String?, interfaces: Array<out String>?,
                    ) {
                        ownerInternal = name
                    }

                    override fun visitSource(source: String?, debug: String?) {
                        sourceFile = source
                    }

                    override fun visitMethod(
                        access: Int, name: String, descriptor: String,
                        signature: String?, exceptions: Array<out String>?,
                    ): MethodVisitor {
                        return object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitMethodInsn(
                                opcode: Int, owner: String, name: String,
                                descriptor: String, isInterface: Boolean,
                            ) {
                                if (name == methodName && (owner == targetInternal || owner == "$targetInternal\$Companion")) {
                                    callsTarget = true
                                }
                            }

                            override fun visitInvokeDynamicInsn(
                                name: String, descriptor: String,
                                bootstrapMethodHandle: org.objectweb.asm.Handle,
                                vararg bootstrapMethodArguments: Any,
                            ) {
                                for (arg in bootstrapMethodArguments) {
                                    if (arg is org.objectweb.asm.Handle &&
                                        arg.name == methodName &&
                                        (arg.owner == targetInternal || arg.owner == "$targetInternal\$Companion")
                                    ) {
                                        callsTarget = true
                                    }
                                }
                            }
                        }
                    }
                },
                ClassReader.SKIP_FRAMES,
            )

            if (callsTarget && sourceFile != null) {
                val packagePath = ownerInternal.substringBeforeLast('/', "")
                val relativePath = if (packagePath.isEmpty()) sourceFile!! else "$packagePath/$sourceFile"
                callSiteFiles.add(relativePath)
            }
        }

        return callSiteFiles
    }

    /**
     * Find classes that implement or extend [className].
     * Returns their fully qualified names.
     */
    fun findImplementors(
        classesRoots: List<File>,
        className: String,
    ): Set<String> {
        val targetInternal = className.replace('.', '/')
        val implementors = mutableSetOf<String>()

        forEachClassFile(classesRoots) { classFile ->
            val reader = ClassReader(classFile.readBytes())

            reader.accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(
                        version: Int, access: Int, name: String,
                        signature: String?, superName: String?, interfaces: Array<out String>?,
                    ) {
                        val isImpl = interfaces?.contains(targetInternal) == true
                            || superName == targetInternal
                        if (isImpl) {
                            implementors.add(ClassName.fromInternal(name).toString())
                        }
                    }
                },
                ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG,
            )
        }

        return implementors
    }

    /**
     * The full "override family" of [className].[methodName]: every class in the type hierarchy that
     * declares this method and must be renamed together to keep overrides valid. Renaming a method on an
     * `Impl` in isolation leaves its interface (and sibling implementors) with the old name, so the impl
     * then `overrides nothing` — a compile error. This walks *up* from [className] to the interface(s)/
     * superclass(es) that declare the method, then *down* from those roots to every implementor/subclass
     * that declares it. Returns FQNs (the target itself included). Empty if [className] isn't found.
     */
    fun findOverrideFamily(
        classesRoots: List<File>,
        className: String,
        methodName: String,
    ): Set<String> {
        val targetInternal = className.replace('.', '/')
        val supertypesOf = mutableMapOf<String, Set<String>>()
        val subtypesOf = mutableMapOf<String, MutableSet<String>>()
        val declaresMethod = mutableSetOf<String>()
        val allClasses = mutableSetOf<String>()

        forEachClassFile(classesRoots) { classFile ->
            val reader = ClassReader(classFile.readBytes())
            reader.accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    var current = ""
                    override fun visit(
                        version: Int, access: Int, name: String,
                        signature: String?, superName: String?, interfaces: Array<out String>?,
                    ) {
                        current = name
                        allClasses.add(name)
                        val supers = buildSet {
                            superName?.let { if (it != "java/lang/Object") add(it) }
                            interfaces?.forEach { add(it) }
                        }
                        supertypesOf[name] = supers
                        supers.forEach { subtypesOf.getOrPut(it) { mutableSetOf() }.add(name) }
                    }

                    override fun visitMethod(
                        access: Int, name: String, descriptor: String?,
                        signature: String?, exceptions: Array<out String>?,
                    ): MethodVisitor? {
                        if (name == methodName) declaresMethod.add(current)
                        return null
                    }
                },
                ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG,
            )
        }

        if (targetInternal !in allClasses) return emptySet()

        // Walk up to the declaring ancestors (transitively).
        val declaringAncestors = mutableSetOf<String>()
        val seenUp = mutableSetOf<String>()
        fun walkUp(node: String) {
            for (parent in supertypesOf[node].orEmpty()) {
                if (!seenUp.add(parent)) continue
                if (parent in declaresMethod) declaringAncestors.add(parent)
                walkUp(parent)
            }
        }
        walkUp(targetInternal)

        // Family roots: the topmost declarers if any, otherwise the target itself.
        val roots = declaringAncestors.ifEmpty { setOf(targetInternal) }

        // Walk down from each root, collecting every descendant that declares the method.
        val family = mutableSetOf<String>()
        val seenDown = mutableSetOf<String>()
        fun walkDown(node: String) {
            if (node in declaresMethod) family.add(node)
            for (sub in subtypesOf[node].orEmpty()) {
                if (!seenDown.add(sub)) continue
                walkDown(sub)
            }
        }
        for (root in roots) {
            family.add(root)
            walkDown(root)
        }
        return family.map { ClassName.fromInternal(it).toString() }.toSet()
    }

    private fun forEachClassFile(classesRoots: List<File>, action: (File) -> Unit) {
        for (root in classesRoots) {
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { action(it) }
        }
    }
}
