package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.types.ClassName
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Computes CBO (Coupling Between Objects): distinct non-JDK/non-stdlib types referenced in
 * field types, method parameter/return types, and local variable types. Unlike fan-out
 * (cnavComplexity/cnavDsm), this is signature-based only — it does not follow method-call targets.
 */
object TypeCouplingAnalyzer {

    fun analyze(reader: ClassReader, className: ClassName): Int {
        val referenced = mutableSetOf<ClassName>()

        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitField(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    value: Any?,
                ): FieldVisitor? {
                    addDescriptorTypes(descriptor, referenced)
                    return null
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    addDescriptorTypes(descriptor, referenced)

                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitLocalVariable(
                            name: String,
                            descriptor: String,
                            signature: String?,
                            start: Label?,
                            end: Label?,
                            index: Int,
                        ) {
                            addDescriptorTypes(descriptor, referenced)
                        }
                    }
                }
            },
            ClassReader.SKIP_FRAMES,
        )

        referenced.remove(className)
        return referenced.count { !it.isExcludedFromCbo() }
    }

    private fun addDescriptorTypes(descriptor: String, referenced: MutableSet<ClassName>) {
        val type = runCatching { Type.getType(descriptor) }.getOrNull() ?: return
        when (type.sort) {
            Type.METHOD -> {
                addType(type.returnType, referenced)
                type.argumentTypes.forEach { addType(it, referenced) }
            }
            else -> addType(type, referenced)
        }
    }

    private fun addType(type: Type, referenced: MutableSet<ClassName>) {
        when (type.sort) {
            Type.OBJECT -> referenced += ClassName.fromInternal(type.internalName)
            Type.ARRAY -> addType(type.elementType, referenced)
        }
    }

    private fun ClassName.isExcludedFromCbo(): Boolean {
        val pkg = packageName().value
        return pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg == "kotlin" || pkg.startsWith("kotlin.")
    }
}
