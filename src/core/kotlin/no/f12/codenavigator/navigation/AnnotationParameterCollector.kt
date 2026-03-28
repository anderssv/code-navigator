package no.f12.codenavigator.navigation

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Creates an [AnnotationVisitor] that collects annotation parameters
 * (simple values, enums, arrays, nested annotations) into a
 * `Map<String, String>` and passes the result to [onComplete].
 *
 * Shared by [no.f12.codenavigator.navigation.annotation.AnnotationExtractor]
 * and [no.f12.codenavigator.navigation.classinfo.ClassDetailExtractor].
 */
fun annotationParameterVisitor(onComplete: (Map<String, String>) -> Unit): AnnotationVisitor {
    val parameters = mutableMapOf<String, String>()
    return object : AnnotationVisitor(Opcodes.ASM9) {
        override fun visit(paramName: String?, value: Any?) {
            if (paramName != null && value != null) {
                parameters[paramName] = value.toString()
            }
        }

        override fun visitEnum(paramName: String?, descriptor: String, value: String) {
            if (paramName != null) {
                val enumClass = typeSimpleName(descriptor)
                parameters[paramName] = "$enumClass.$value"
            }
        }

        override fun visitArray(paramName: String?): AnnotationVisitor? {
            if (paramName == null) return null
            val elements = mutableListOf<String>()
            return object : AnnotationVisitor(Opcodes.ASM9) {
                override fun visit(name: String?, value: Any?) {
                    if (value != null) {
                        elements.add(value.toString())
                    }
                }

                override fun visitEnum(name: String?, descriptor: String, value: String) {
                    val enumClass = typeSimpleName(descriptor)
                    elements.add("$enumClass.$value")
                }

                override fun visitEnd() {
                    parameters[paramName] = when (elements.size) {
                        0 -> "[]"
                        1 -> elements.first()
                        else -> "[${elements.joinToString(", ")}]"
                    }
                }
            }
        }

        override fun visitAnnotation(paramName: String?, descriptor: String): AnnotationVisitor? {
            if (paramName == null) return null
            val nestedName = typeSimpleName(descriptor)
            val nestedParams = mutableMapOf<String, String>()
            return object : AnnotationVisitor(Opcodes.ASM9) {
                override fun visit(name: String?, value: Any?) {
                    if (name != null && value != null) {
                        nestedParams[name] = value.toString()
                    }
                }

                override fun visitEnd() {
                    val paramStr = if (nestedParams.isEmpty()) {
                        "@$nestedName"
                    } else {
                        val entries = nestedParams.entries.joinToString(", ") { "${it.key}=${it.value}" }
                        "@$nestedName($entries)"
                    }
                    parameters[paramName] = paramStr
                }
            }
        }

        override fun visitEnd() {
            onComplete(parameters.toMap())
        }
    }
}

private fun typeSimpleName(descriptor: String): String =
    Type.getType(descriptor).className.substringAfterLast('.')
