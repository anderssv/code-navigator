package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

data class FieldDetail(
    val name: String,
    val type: String,
)

data class MethodDetail(
    val name: String,
    val parameterTypes: List<String>,
    val returnType: String,
)

data class ClassDetail(
    val className: ClassName,
    val sourceFile: String,
    val superClass: ClassName?,
    val interfaces: List<ClassName>,
    val fields: List<FieldDetail>,
    val methods: List<MethodDetail>,
)

object ClassDetailExtractor {

    fun extract(classFile: File): ClassDetail {
        val reader = createClassReader(classFile)
        var className = ClassName("")
        var sourceFile = "<unknown>"
        var superClass: ClassName? = null
        var interfaceList = emptyList<ClassName>()
        val methods = mutableListOf<MethodDetail>()
        val fields = mutableListOf<FieldDetail>()
        val fieldNames = mutableSetOf<String>()

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
                    superClass = superName
                        ?.takeIf { it != "java/lang/Object" }
                        ?.let { ClassName.fromInternal(it) }
                    interfaceList = interfaces
                        ?.map { ClassName.fromInternal(it) }
                        ?: emptyList()
                }

                override fun visitSource(source: String?, debug: String?) {
                    if (source != null) {
                        sourceFile = source
                    }
                }

                override fun visitField(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    value: Any?,
                ): FieldVisitor? {
                    if (access and Opcodes.ACC_SYNTHETIC != 0) return null
                    if (name in KotlinMethodFilter.EXCLUDED_FIELDS) return null

                    fieldNames.add(name)
                    fields.add(FieldDetail(name, simplifyType(Type.getType(descriptor))))
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

                    val argTypes = Type.getArgumentTypes(descriptor).map { simplifyType(it) }
                    val retType = simplifyType(Type.getReturnType(descriptor))
                    methods.add(MethodDetail(name, argTypes, retType))
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
        )

        val filteredMethods = methods.filter { method ->
            !KotlinMethodFilter.isAccessorForField(method.name, fieldNames)
        }

        return ClassDetail(
            className = className,
            sourceFile = sourceFile,
            superClass = superClass,
            interfaces = interfaceList,
            fields = fields,
            methods = filteredMethods,
        )
    }

    private fun simplifyType(type: Type): String = when (type.sort) {
        Type.VOID -> "void"
        Type.BOOLEAN -> "boolean"
        Type.CHAR -> "char"
        Type.BYTE -> "byte"
        Type.SHORT -> "short"
        Type.INT -> "int"
        Type.FLOAT -> "float"
        Type.LONG -> "long"
        Type.DOUBLE -> "double"
        Type.ARRAY -> "${simplifyType(type.elementType)}[]"
        Type.OBJECT -> type.className.substringAfterLast('.')
        else -> type.className
    }
}
