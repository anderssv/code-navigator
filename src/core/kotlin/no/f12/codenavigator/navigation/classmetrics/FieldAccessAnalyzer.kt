package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.bytecode.KotlinMethodFilter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

data class FieldAccessData(
    val fields: Set<String>,
    val fieldAccessByMethod: Map<String, Set<String>>,
)

/**
 * Tracks which instance fields each eligible method accesses (GETFIELD/PUTFIELD on `this`).
 * Excludes constructors, static initializers, and Kotlin-generated property accessors —
 * a default getter/setter trivially touches exactly one field and would inflate cohesion.
 */
object FieldAccessAnalyzer {

    fun analyze(reader: ClassReader): FieldAccessData {
        val fields = mutableSetOf<String>()
        val fieldAccessByMethod = mutableMapOf<String, Set<String>>()
        var internalName = ""

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
                    internalName = name
                }

                override fun visitField(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    value: Any?,
                ): FieldVisitor? {
                    if (name !in KotlinMethodFilter.EXCLUDED_FIELDS) {
                        fields += name
                    }
                    return null
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (KotlinMethodFilter.isExcludedMethod(name, access)) return null
                    if (KotlinMethodFilter.isAccessorForField(name, fields)) return null

                    val accessed = mutableSetOf<String>()
                    fieldAccessByMethod["$name$descriptor"] = accessed

                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitFieldInsn(opcode: Int, owner: String, fieldName: String, fieldDescriptor: String) {
                            if (owner == internalName && (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD)) {
                                accessed += fieldName
                            }
                        }
                    }
                }
            },
            ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG,
        )

        return FieldAccessData(fields, fieldAccessByMethod)
    }
}
