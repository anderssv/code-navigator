package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.core.createClassReader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

/**
 * Scans bytecode for Kotlin file-facade classes (`*Kt`) and extracts the
 * first parameter type of each static method. These represent Kotlin
 * extension function receiver types.
 *
 * Used by dead code analysis to detect framework entry points in Ktor and
 * similar DSL-based frameworks where extension functions on Route or
 * Application are invoked by the framework.
 */
object ReceiverTypeExtractor {

    fun scanAll(classDirectories: List<File>): Map<ClassName, Set<ClassName>> {
        val result = mutableMapOf<ClassName, MutableSet<ClassName>>()

        for (dir in classDirectories) {
            if (!dir.exists()) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { classFile ->
                    try {
                        val (className, receiverTypes) = extract(classFile)
                        if (receiverTypes.isNotEmpty()) {
                            result.getOrPut(className) { mutableSetOf() }.addAll(receiverTypes)
                        }
                    } catch (_: UnsupportedBytecodeVersionException) {
                        // Skip files we can't read
                    }
                }
        }

        return result
    }

    private fun extract(classFile: File): Pair<ClassName, Set<ClassName>> {
        val reader = createClassReader(classFile)
        var className = ClassName("")
        val receiverTypes = mutableSetOf<ClassName>()

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
                    className = ClassName.fromInternal(name)
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (!className.value.endsWith("Kt")) return null
                    if (access and Opcodes.ACC_STATIC == 0) return null
                    if (name.startsWith("<")) return null

                    val argTypes = Type.getArgumentTypes(descriptor)
                    if (argTypes.isNotEmpty() && argTypes[0].sort == Type.OBJECT) {
                        receiverTypes.add(ClassName(argTypes[0].className))
                    }
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
        )

        return className to receiverTypes
    }
}
