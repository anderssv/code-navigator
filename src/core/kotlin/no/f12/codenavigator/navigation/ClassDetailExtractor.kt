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
    val className: String,
    val sourceFile: String,
    val superClass: String?,
    val interfaces: List<String>,
    val fields: List<FieldDetail>,
    val methods: List<MethodDetail>,
)

object ClassDetailExtractor {

    private val EXCLUDED_METHODS = setOf(
        "<init>", "<clinit>",
        "toString", "hashCode", "equals",
        "copy",
    )

    private val DATA_CLASS_COMPONENT = Regex("""^component\d+$""")
    private val KOTLIN_ACCESSOR = Regex("""^(get|set|is)[A-Z]""")
    private val LAMBDA_METHOD = Regex("""\${'$'}lambda\${'$'}""")
    private val SYNTHETIC_PREFIX = "access$"
    private val EXCLUDED_FIELDS = setOf("INSTANCE")

    fun extract(classFile: File): ClassDetail {
        val reader = createClassReader(classFile)
        var className = ""
        var sourceFile = "<unknown>"
        var superClass: String? = null
        var interfaceList = emptyList<String>()
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
                    className = name.replace('/', '.')
                    superClass = superName
                        ?.takeIf { it != "java/lang/Object" }
                        ?.replace('/', '.')
                    interfaceList = interfaces
                        ?.map { it.replace('/', '.') }
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
                    if (name in EXCLUDED_FIELDS) return null

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
                    if (isExcludedMethod(name, access)) return null

                    val argTypes = Type.getArgumentTypes(descriptor).map { simplifyType(it) }
                    val retType = simplifyType(Type.getReturnType(descriptor))
                    methods.add(MethodDetail(name, argTypes, retType))
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
        )

        val filteredMethods = methods.filter { method ->
            !isAccessorForField(method.name, fieldNames)
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

    private fun isExcludedMethod(name: String, access: Int): Boolean {
        if (name in EXCLUDED_METHODS) return true
        if (name.startsWith(SYNTHETIC_PREFIX)) return true
        if (access and Opcodes.ACC_SYNTHETIC != 0) return true
        if (DATA_CLASS_COMPONENT.matches(name)) return true
        if (name.startsWith("copy$")) return true
        if (LAMBDA_METHOD.containsMatchIn(name)) return true
        return false
    }

    private fun isAccessorForField(methodName: String, fieldNames: Set<String>): Boolean {
        if (!KOTLIN_ACCESSOR.containsMatchIn(methodName)) return false

        val prefix = when {
            methodName.startsWith("get") -> "get"
            methodName.startsWith("set") -> "set"
            methodName.startsWith("is") -> "is"
            else -> return false
        }
        val propertyName = methodName.removePrefix(prefix).replaceFirstChar { it.lowercase() }
        return propertyName in fieldNames
    }
}
