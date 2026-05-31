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

    private fun forEachClassFile(classesRoots: List<File>, action: (File) -> Unit) {
        for (root in classesRoots) {
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { action(it) }
        }
    }
}
