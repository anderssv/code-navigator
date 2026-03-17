package no.f12.codenavigator

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File

data class ClassInfo(
    val className: String,
    val sourceFileName: String,
    val reconstructedSourcePath: String,
    val isUserDefinedClass: Boolean,
)

object ClassInfoExtractor {

    private val SYNTHETIC_SUFFIX = Regex("""\$\d+$""")
    private val LAMBDA_PATTERN = Regex("""\${'$'}lambda\${'$'}""")

    fun extract(classFile: File): ClassInfo {
        val reader = ClassReader(classFile.readBytes())
        var internalName = ""
        var sourceFile: String? = null

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

                override fun visitSource(source: String?, debug: String?) {
                    sourceFile = source
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG.inv() or ClassReader.SKIP_FRAMES,
        )

        val dottedName = internalName.replace('/', '.').replace('$', '.')
        val isUserDefined = !SYNTHETIC_SUFFIX.containsMatchIn(internalName) &&
            !LAMBDA_PATTERN.containsMatchIn(internalName)

        val packageDir = internalName.substringBeforeLast('/', "")
        val sourceFileName = sourceFile
        val reconstructedPath = when {
            sourceFileName == null -> "<unknown>"
            packageDir.isNotEmpty() -> "$packageDir/$sourceFileName"
            else -> sourceFileName
        }

        return ClassInfo(
            className = dottedName,
            sourceFileName = sourceFile ?: "<unknown>",
            reconstructedSourcePath = reconstructedPath,
            isUserDefinedClass = isUserDefined,
        )
    }
}
