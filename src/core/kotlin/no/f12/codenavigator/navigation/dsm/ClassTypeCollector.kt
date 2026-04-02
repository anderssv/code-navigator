package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.createClassReader
import no.f12.codenavigator.navigation.core.UnsupportedBytecodeVersionException
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

enum class ClassKind {
    INTERFACE,
    ABSTRACT,
    DATA_CLASS,
    RECORD,
    ANNOTATED_MODEL,
    CONCRETE,
}

object ClassTypeCollector {

    fun collect(
        classDirectories: List<File>,
        modelAnnotations: Set<String> = emptySet(),
    ): Map<ClassName, ClassKind> {
        val modelDescriptors = modelAnnotations.map { "L${it.replace('.', '/')};" }.toSet()
        val registry = mutableMapOf<ClassName, ClassKind>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            val reader = createClassReader(classFile)
                            val classifier = ClassKindVisitor(modelDescriptors)
                            reader.accept(classifier, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                            val className = ClassName.fromInternal(reader.className)
                            registry[className] = classifier.classKind()
                        } catch (_: UnsupportedBytecodeVersionException) {
                            // skip unsupported bytecode versions
                        }
                    }
            }

        return registry
    }
}

private class ClassKindVisitor(
    private val modelDescriptors: Set<String>,
) : ClassVisitor(Opcodes.ASM9) {
    private var access: Int = 0
    private var hasComponent1 = false
    private var hasCopy = false
    private var hasModelAnnotation = false

    override fun visit(
        version: Int, access: Int, name: String?, signature: String?,
        superName: String?, interfaces: Array<out String>?,
    ) {
        this.access = access
    }

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        if (descriptor != null && descriptor in modelDescriptors) {
            hasModelAnnotation = true
        }
        return null
    }

    override fun visitMethod(
        access: Int, name: String?, descriptor: String?,
        signature: String?, exceptions: Array<out String>?,
    ): MethodVisitor? {
        when (name) {
            "component1" -> hasComponent1 = true
            "copy" -> hasCopy = true
        }
        return null
    }

    fun classKind(): ClassKind = when {
        access and Opcodes.ACC_INTERFACE != 0 -> ClassKind.INTERFACE
        access and Opcodes.ACC_RECORD != 0 -> ClassKind.RECORD
        hasModelAnnotation -> ClassKind.ANNOTATED_MODEL
        access and Opcodes.ACC_ABSTRACT != 0 -> ClassKind.ABSTRACT
        hasComponent1 && hasCopy -> ClassKind.DATA_CLASS
        else -> ClassKind.CONCRETE
    }
}
