package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.navigation.core.AnnotationName
import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.KotlinMethodFilter
import no.f12.codenavigator.navigation.core.ResolvedAnnotation
import no.f12.codenavigator.navigation.core.unwrappingAnnotationVisitor
import no.f12.codenavigator.navigation.core.createClassReader
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

data class AnnotationDetail(
    val name: AnnotationName,
    val parameters: Map<String, String>,
)

data class FieldDetail(
    val name: String,
    val type: String,
    val annotations: List<AnnotationDetail>,
)

data class MethodDetail(
    val name: String,
    val parameterTypes: List<String>,
    val returnType: String,
    val annotations: List<AnnotationDetail>,
)

data class ClassDetail(
    val className: ClassName,
    val sourceFile: String,
    val superClass: ClassName?,
    val interfaces: List<ClassName>,
    val fields: List<FieldDetail>,
    val methods: List<MethodDetail>,
    val annotations: List<AnnotationDetail>,
)

object ClassDetailExtractor {

    fun extract(classFile: File): ClassDetail =
        extract(createClassReader(classFile))

    fun extract(bytes: ByteArray): ClassDetail =
        extract(createClassReader(bytes))

    private fun extract(reader: ClassReader): ClassDetail {
        var className = ClassName("")
        var sourceFile = "<unknown>"
        var superClass: ClassName? = null
        var interfaceList = emptyList<ClassName>()
        val methods = mutableListOf<MethodDetail>()
        val fields = mutableListOf<FieldDetail>()
        val fieldNames = mutableSetOf<String>()
        val classAnnotations = mutableListOf<AnnotationDetail>()

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

                override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? =
                    collectAnnotation(descriptor, classAnnotations)

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
                    val fieldType = simplifyType(Type.getType(descriptor))
                    val fieldAnnotations = mutableListOf<AnnotationDetail>()

                    return object : FieldVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? =
                            collectAnnotation(descriptor, fieldAnnotations)

                        override fun visitEnd() {
                            fields.add(FieldDetail(name, fieldType, fieldAnnotations.toList()))
                        }
                    }
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
                    val methodAnnotations = mutableListOf<AnnotationDetail>()

                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? =
                            collectAnnotation(descriptor, methodAnnotations)

                        override fun visitEnd() {
                            methods.add(MethodDetail(name, argTypes, retType, methodAnnotations.toList()))
                        }
                    }
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
            annotations = classAnnotations,
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

    private fun annotationFqn(descriptor: String): AnnotationName =
        AnnotationName(Type.getType(descriptor).className)

    private fun collectAnnotation(
        descriptor: String?,
        annotations: MutableList<AnnotationDetail>,
    ): AnnotationVisitor? {
        if (descriptor == null) return null
        val fqn = annotationFqn(descriptor)
        if (fqn.isInternal()) return null
        return unwrappingAnnotationVisitor(descriptor) { resolved ->
            for (ann in resolved) {
                annotations.add(AnnotationDetail(annotationFqn(ann.descriptor), ann.parameters))
            }
        }
    }
}
