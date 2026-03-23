package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

object DsmDependencyExtractor {

    fun extract(classDirectories: List<File>, rootPrefix: String): List<PackageDependency> {
        val dependencies = mutableSetOf<PackageDependency>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        extractFromClass(classFile, rootPrefix, dependencies)
                    }
            }

        return dependencies.toList()
    }

    private fun extractFromClass(
        classFile: File,
        rootPrefix: String,
        dependencies: MutableSet<PackageDependency>,
    ) {
        val reader = ClassReader(classFile.readBytes())
        val collector = DependencyCollector(rootPrefix)
        reader.accept(collector, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        val sourceClass = reader.className.replace('/', '.')
        val sourcePackage = sourceClass.substringBeforeLast('.', "")

        if (rootPrefix.isNotEmpty() && !sourceClass.startsWith(rootPrefix)) return

        collector.referencedTypes
            .filter { it != sourceClass }
            .filter { rootPrefix.isEmpty() || it.startsWith(rootPrefix) }
            .forEach { targetClass ->
                val targetPackage = targetClass.substringBeforeLast('.', "")
                if (targetPackage != sourcePackage) {
                    dependencies += PackageDependency(sourcePackage, targetPackage, sourceClass, targetClass)
                }
            }
    }
}

private class DependencyCollector(private val rootPrefix: String) : ClassVisitor(Opcodes.ASM9) {
    val referencedTypes = mutableSetOf<String>()

    override fun visit(
        version: Int, access: Int, name: String?, signature: String?,
        superName: String?, interfaces: Array<out String>?,
    ) {
        superName?.let { addInternalName(it) }
        interfaces?.forEach { addInternalName(it) }
    }

    override fun visitField(
        access: Int, name: String?, descriptor: String?,
        signature: String?, value: Any?,
    ): FieldVisitor? {
        descriptor?.let { addDescriptorTypes(it) }
        return null
    }

    override fun visitMethod(
        access: Int, name: String?, descriptor: String?,
        signature: String?, exceptions: Array<out String>?,
    ): MethodVisitor {
        descriptor?.let { addDescriptorTypes(it) }
        exceptions?.forEach { addInternalName(it) }

        return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitMethodInsn(
                opcode: Int, owner: String?, name: String?,
                descriptor: String?, isInterface: Boolean,
            ) {
                owner?.let { addInternalName(it) }
                descriptor?.let { addDescriptorTypes(it) }
            }

            override fun visitFieldInsn(
                opcode: Int, owner: String?, name: String?,
                descriptor: String?,
            ) {
                owner?.let { addInternalName(it) }
                descriptor?.let { addDescriptorTypes(it) }
            }

            override fun visitTypeInsn(opcode: Int, type: String?) {
                type?.let { addInternalName(it) }
            }

            override fun visitLdcInsn(value: Any?) {
                if (value is Type) addType(value)
            }
        }
    }

    private fun addInternalName(internalName: String) {
        val className = internalName.replace('/', '.')
        if (className.startsWith('[')) return
        if (rootPrefix.isNotEmpty() && !className.startsWith(rootPrefix)) return
        val baseName = className.substringBefore('$')
        referencedTypes += baseName
    }

    private fun addDescriptorTypes(descriptor: String) {
        val type = runCatching { Type.getType(descriptor) }.getOrNull() ?: return
        when (type.sort) {
            Type.METHOD -> {
                addType(type.returnType)
                type.argumentTypes.forEach { addType(it) }
            }
            else -> addType(type)
        }
    }

    private fun addType(type: Type) {
        when (type.sort) {
            Type.OBJECT -> addInternalName(type.internalName)
            Type.ARRAY -> addType(type.elementType)
        }
    }
}
