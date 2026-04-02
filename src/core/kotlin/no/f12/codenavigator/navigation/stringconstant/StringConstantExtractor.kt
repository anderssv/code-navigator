package no.f12.codenavigator.navigation.stringconstant

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.KotlinMethodFilter
import no.f12.codenavigator.navigation.core.createClassReader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

data class StringConstantMatch(
    val className: ClassName,
    val methodName: String,
    val value: String,
    val sourceFile: String,
)

object StringConstantExtractor {

    fun extract(classFile: File): List<StringConstantMatch> {
        val reader = createClassReader(classFile)
        val results = mutableListOf<StringConstantMatch>()
        var className = ""
        var sourceFile = ""

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
                    className = name.replace('/', '.')
                    sourceFile = ""
                }

                override fun visitSource(source: String?, debug: String?) {
                    sourceFile = source ?: ""
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (KotlinMethodFilter.isGenerated(name)) return null

                    val methodName = name
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitLdcInsn(value: Any?) {
                            if (value is String) {
                                results.add(
                                    StringConstantMatch(
                                        className = ClassName(className),
                                        methodName = methodName,
                                        value = value,
                                        sourceFile = sourceFile,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
            0,
        )

        return results
    }
}
