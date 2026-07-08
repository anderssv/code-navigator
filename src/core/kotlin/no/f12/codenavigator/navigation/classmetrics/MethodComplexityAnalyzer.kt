package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.bytecode.KotlinMethodFilter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Computes WMC (sum of McCabe cyclomatic complexity across eligible methods) via bytecode
 * branch counting: base 1 per method, +1 per conditional jump, +1 per switch case, +1 per catch block.
 * Uses the same method-eligibility filter as [FieldAccessAnalyzer] so `totalMethods` stays consistent.
 */
object MethodComplexityAnalyzer {

    private val CONDITIONAL_JUMP_OPCODES = setOf(
        Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
        Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
        Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.IFNULL, Opcodes.IFNONNULL,
    )

    fun analyze(reader: ClassReader, fields: Set<String>): Int {
        var totalWmc = 0

        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (KotlinMethodFilter.isExcludedMethod(name, access)) return null
                    if (KotlinMethodFilter.isAccessorForField(name, fields)) return null

                    totalWmc += 1

                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitJumpInsn(opcode: Int, label: Label?) {
                            if (opcode in CONDITIONAL_JUMP_OPCODES) totalWmc++
                        }

                        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {
                            totalWmc += labels.size
                        }

                        override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) {
                            totalWmc += labels?.size ?: 0
                        }

                        override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
                            if (type != null) totalWmc++
                        }
                    }
                }
            },
            ClassReader.SKIP_FRAMES,
        )

        return totalWmc
    }
}
