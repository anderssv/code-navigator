package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.bytecode.createClassReader
import no.f12.codenavigator.navigation.bytecode.UnsupportedBytecodeVersionException
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile

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

    /**
     * Resolves specific classes from classpath JARs/directories that are not already in the registry.
     * Only scans entries needed for the given [targetClasses], avoiding full JAR scans.
     */
    fun resolveFromClasspath(
        targetClasses: Set<ClassName>,
        classpath: List<Path>,
        modelAnnotations: Set<String> = emptySet(),
    ): Map<ClassName, ClassKind> {
        if (targetClasses.isEmpty() || classpath.isEmpty()) return emptyMap()

        val modelDescriptors = modelAnnotations.map { "L${it.replace('.', '/')};" }.toSet()
        val resolved = mutableMapOf<ClassName, ClassKind>()
        val remaining = targetClasses.toMutableSet()

        // Build lookup: className -> internal path in JAR
        val targetPaths = remaining.associateBy { it.value.replace('.', '/') + ".class" }

        for (path in classpath) {
            if (remaining.isEmpty()) break
            val file = path.toFile()
            if (!file.exists()) continue

            if (file.isDirectory) {
                for ((entryPath, className) in targetPaths) {
                    if (className !in remaining) continue
                    val classFile = File(file, entryPath)
                    if (classFile.exists()) {
                        try {
                            val reader = createClassReader(classFile)
                            val classifier = ClassKindVisitor(modelDescriptors)
                            reader.accept(classifier, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                            resolved[className] = classifier.classKind()
                            remaining.remove(className)
                        } catch (_: Exception) { }
                    }
                }
            } else if (file.extension == "jar") {
                try {
                    JarFile(file).use { jar ->
                        for ((entryPath, className) in targetPaths) {
                            if (className !in remaining) continue
                            val entry = jar.getJarEntry(entryPath) ?: continue
                            try {
                                val bytes = jar.getInputStream(entry).readBytes()
                                val reader = ClassReader(bytes)
                                val classifier = ClassKindVisitor(modelDescriptors)
                                reader.accept(classifier, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                                resolved[className] = classifier.classKind()
                                remaining.remove(className)
                            } catch (_: Exception) { }
                        }
                    }
                } catch (_: Exception) { }
            }
        }

        return resolved
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
