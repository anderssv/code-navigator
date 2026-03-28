package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.navigation.AnnotationName
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.KotlinMethodFilter
import no.f12.codenavigator.navigation.MethodRef
import no.f12.codenavigator.navigation.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.createClassReader
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

data class AnnotationScanResult(
    val className: ClassName,
    val sourceFile: String?,
    val classAnnotations: Set<AnnotationName>,
    val methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
)

/**
 * Lightweight annotation-only scanner. Reads class-level and method-level
 * annotation simple names from bytecode without full detail extraction.
 * Skips constructors and synthetic methods.
 */
object AnnotationExtractor {

    fun extract(classFile: File): AnnotationScanResult {
        val reader = createClassReader(classFile)
        var className = ClassName("")
        var sourceFile: String? = null
        val classAnnotations = mutableSetOf<AnnotationName>()
        val methodAnnotations = mutableMapOf<MethodRef, Set<AnnotationName>>()

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
                }

                override fun visitSource(source: String?, debug: String?) {
                    sourceFile = source
                }

                override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                    if (descriptor != null) {
                        classAnnotations.add(annotationFqn(descriptor))
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
                    if (name.startsWith("<") || KotlinMethodFilter.isGenerated(name)) {
                        return null
                    }
                    val methodRef = MethodRef(className, name)
                    val annotations = mutableSetOf<AnnotationName>()

                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                            if (descriptor != null) {
                                annotations.add(annotationFqn(descriptor))
                            }
                            return null
                        }

                        override fun visitEnd() {
                            if (annotations.isNotEmpty()) {
                                methodAnnotations[methodRef] = annotations
                            }
                        }
                    }
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
        )

        return AnnotationScanResult(
            className = className,
            sourceFile = sourceFile,
            classAnnotations = classAnnotations,
            methodAnnotations = methodAnnotations,
        )
    }

    /**
     * Scans all class files in the given directories and builds class-level
     * and method-level annotation maps. Only includes entries that have
     * at least one annotation.
     */
    fun scanAll(classDirectories: List<File>): Pair<Map<ClassName, Set<AnnotationName>>, Map<MethodRef, Set<AnnotationName>>> {
        val classAnnotations = mutableMapOf<ClassName, Set<AnnotationName>>()
        val methodAnnotations = mutableMapOf<MethodRef, Set<AnnotationName>>()

        for (dir in classDirectories) {
            dir.walk()
                .filter { it.isFile && it.extension == "class" }
                .forEach { classFile ->
                    try {
                        val result = extract(classFile)
                        if (result.classAnnotations.isNotEmpty()) {
                            classAnnotations[result.className] = result.classAnnotations
                        }
                        methodAnnotations.putAll(result.methodAnnotations)
                    } catch (_: UnsupportedBytecodeVersionException) {
                        // Skip files we can't read
                    }
                }
        }

        return Pair(classAnnotations, methodAnnotations)
    }

    private fun annotationFqn(descriptor: String): AnnotationName =
        AnnotationName(Type.getType(descriptor).className)
}
