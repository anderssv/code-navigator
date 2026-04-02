package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.createClassReader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File

data class ClassInfo(
    val className: ClassName,
    val sourceFileName: String,
    val reconstructedSourcePath: String,
    val isUserDefinedClass: Boolean,
)

object ClassInfoExtractor {

    fun extract(classFile: File): ClassInfo {
        val reader = createClassReader(classFile)
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

        val className = ClassName.fromInternal(internalName)
        val isUserDefined = !className.isSynthetic()

        val packageDir = className.packagePath()
        val sourceFileName = sourceFile
        val reconstructedPath = when {
            sourceFileName == null -> "<unknown>"
            packageDir.isNotEmpty() -> "$packageDir/$sourceFileName"
            else -> sourceFileName
        }

        return ClassInfo(
            className = className,
            sourceFileName = sourceFile ?: "<unknown>",
            reconstructedSourcePath = reconstructedPath,
            isUserDefinedClass = isUserDefined,
        )
    }
}
