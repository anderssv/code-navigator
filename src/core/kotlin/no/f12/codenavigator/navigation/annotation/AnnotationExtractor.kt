package no.f12.codenavigator.navigation.annotation

import no.f12.codenavigator.navigation.AnnotationName
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.KotlinMethodFilter
import no.f12.codenavigator.navigation.unwrappingAnnotationVisitor
import no.f12.codenavigator.navigation.callgraph.MethodRef
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
    val classAnnotationParameters: Map<AnnotationName, Map<String, String>> = emptyMap(),
    val methodAnnotationParameters: Map<MethodRef, Map<AnnotationName, Map<String, String>>> = emptyMap(),
)

data class AggregatedAnnotations(
    val classAnnotations: Map<ClassName, Set<AnnotationName>>,
    val methodAnnotations: Map<MethodRef, Set<AnnotationName>>,
    val classAnnotationParameters: Map<ClassName, Map<AnnotationName, Map<String, String>>>,
    val methodAnnotationParameters: Map<MethodRef, Map<AnnotationName, Map<String, String>>>,
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
        val classAnnotationParams = mutableMapOf<AnnotationName, Map<String, String>>()
        val methodAnnotationParams = mutableMapOf<MethodRef, MutableMap<AnnotationName, Map<String, String>>>()

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
                    if (descriptor == null) return null
                    return collectAnnotation(descriptor, classAnnotations, classAnnotationParams)
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
                    val paramMap = mutableMapOf<AnnotationName, Map<String, String>>()

                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                            if (descriptor == null) return null
                            return collectAnnotation(descriptor, annotations, paramMap)
                        }

                        override fun visitEnd() {
                            if (annotations.isNotEmpty()) {
                                methodAnnotations[methodRef] = annotations
                                methodAnnotationParams[methodRef] = paramMap
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
            classAnnotationParameters = classAnnotationParams,
            methodAnnotationParameters = methodAnnotationParams,
        )
    }

    /**
     * Scans all class files in the given directories and builds class-level
     * and method-level annotation maps. Only includes entries that have
     * at least one annotation.
     */
    fun scanAll(classDirectories: List<File>): AggregatedAnnotations {
        val classAnnotations = mutableMapOf<ClassName, Set<AnnotationName>>()
        val methodAnnotations = mutableMapOf<MethodRef, Set<AnnotationName>>()
        val classAnnotationParams = mutableMapOf<ClassName, Map<AnnotationName, Map<String, String>>>()
        val methodAnnotationParams = mutableMapOf<MethodRef, Map<AnnotationName, Map<String, String>>>()

        for (dir in classDirectories) {
            dir.walk()
                .filter { it.isFile && it.extension == "class" }
                .forEach { classFile ->
                    try {
                        val result = extract(classFile)
                        if (result.classAnnotations.isNotEmpty()) {
                            classAnnotations[result.className] = result.classAnnotations
                            classAnnotationParams[result.className] = result.classAnnotationParameters
                        }
                        methodAnnotations.putAll(result.methodAnnotations)
                        methodAnnotationParams.putAll(result.methodAnnotationParameters)
                    } catch (_: UnsupportedBytecodeVersionException) {
                        // Skip files we can't read
                    }
                }
        }

        return AggregatedAnnotations(
            classAnnotations = classAnnotations,
            methodAnnotations = methodAnnotations,
            classAnnotationParameters = classAnnotationParams,
            methodAnnotationParameters = methodAnnotationParams,
        )
    }

    private fun annotationFqn(descriptor: String): AnnotationName =
        AnnotationName(Type.getType(descriptor).className)

    private fun collectAnnotation(
        descriptor: String,
        annotations: MutableSet<AnnotationName>,
        paramTarget: MutableMap<AnnotationName, Map<String, String>>,
    ): AnnotationVisitor? {
        val fqn = annotationFqn(descriptor)
        if (fqn.isInternal()) return null
        return unwrappingAnnotationVisitor(descriptor) { resolved ->
            for (ann in resolved) {
                val name = annotationFqn(ann.descriptor)
                annotations.add(name)
                paramTarget[name] = ann.parameters
            }
        }
    }
}
