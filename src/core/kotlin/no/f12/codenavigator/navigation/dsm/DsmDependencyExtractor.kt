package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.PackageName
import no.f12.codenavigator.navigation.ScanResult
import no.f12.codenavigator.navigation.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.createClassReader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

object DsmDependencyExtractor {

    fun extract(classDirectories: List<File>, rootPrefix: PackageName): ScanResult<List<PackageDependency>> {
        val dependencies = mutableSetOf<PackageDependency>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            extractFromClass(classFile, rootPrefix, dependencies)
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(
            data = dependencies.toList(),
            skippedFiles = skipped,
        )
    }

    fun extract(
        classDirectories: List<File>,
        projectClasses: Set<ClassName>,
        packageFilter: PackageName = PackageName(""),
        includeExternal: Boolean = false,
    ): ScanResult<List<PackageDependency>> {
        val dependencies = mutableSetOf<PackageDependency>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            extractFromClassWithProjectFilter(classFile, projectClasses, packageFilter, includeExternal, dependencies)
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(
            data = dependencies.toList(),
            skippedFiles = skipped,
        )
    }

    private fun extractFromClassWithProjectFilter(
        classFile: File,
        projectClasses: Set<ClassName>,
        packageFilter: PackageName,
        includeExternal: Boolean,
        dependencies: MutableSet<PackageDependency>,
    ) {
        val reader = createClassReader(classFile)
        val collector = DependencyCollector(PackageName(""))
        reader.accept(collector, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        val sourceClass = ClassName.fromInternal(reader.className).topLevelClass()
        val sourcePackage = sourceClass.packageName()

        if (sourceClass !in projectClasses) return
        if (packageFilter.isNotEmpty() && !sourceClass.startsWith(packageFilter)) return

        collector.referencedTypes
            .filter { it != sourceClass }
            .filter { includeExternal || it in projectClasses }
            .filter { packageFilter.isEmpty() || it.startsWith(packageFilter) }
            .forEach { targetClass ->
                val targetPackage = targetClass.packageName()
                if (targetPackage != sourcePackage) {
                    dependencies += PackageDependency(sourcePackage, targetPackage, sourceClass, targetClass)
                }
            }
    }

    private fun extractFromClass(
        classFile: File,
        rootPrefix: PackageName,
        dependencies: MutableSet<PackageDependency>,
    ) {
        val reader = createClassReader(classFile)
        val collector = DependencyCollector(rootPrefix)
        reader.accept(collector, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        val sourceClass = ClassName.fromInternal(reader.className)
        val sourcePackage = sourceClass.packageName()

        if (rootPrefix.isNotEmpty() && !sourceClass.startsWith(rootPrefix)) return

        collector.referencedTypes
            .filter { it != sourceClass }
            .filter { rootPrefix.isEmpty() || it.startsWith(rootPrefix) }
            .forEach { targetClass ->
                val targetPackage = targetClass.packageName()
                if (targetPackage != sourcePackage) {
                    dependencies += PackageDependency(sourcePackage, targetPackage, sourceClass, targetClass)
                }
            }
    }
}

private class DependencyCollector(private val rootPrefix: PackageName) : ClassVisitor(Opcodes.ASM9) {
    val referencedTypes = mutableSetOf<ClassName>()

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
        if (internalName.startsWith('[')) return
        val className = ClassName.fromInternal(internalName)
        if (rootPrefix.isNotEmpty() && !className.startsWith(rootPrefix)) return
        referencedTypes += className.topLevelClass()
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
