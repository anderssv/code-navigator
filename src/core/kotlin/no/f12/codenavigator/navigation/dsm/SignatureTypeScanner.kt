package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.bytecode.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.bytecode.createClassReader
import no.f12.codenavigator.navigation.types.ClassName
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

/**
 * Collects the types each class names in a *signature* position — supertypes, field types, method
 * parameter and return types — and deliberately never visits method bodies. Private fields and
 * methods are excluded too: a private member's type is never visible to callers of the class, so
 * it carries the same weight as a type touched only inside a method body, not a real signature
 * exposure (e.g. a private factory method building a framework-typed object as an internal
 * implementation detail of an otherwise plain composition-root-style class).
 *
 * The flat dependency graph (`PackageDependency`) can't answer "does this class traffic in framework
 * types, or merely touch one internally?", because it carries no edge position. Rather than widen that
 * record — it is the shared currency of DSM, cycles, balance and affinity — this is a separate pass
 * consumed only by adapter classification.
 */
object SignatureTypeScanner {

    fun scan(classDirectories: List<File>): Map<ClassName, Set<ClassName>> {
        val result = mutableMapOf<ClassName, MutableSet<ClassName>>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            val reader = createClassReader(classFile)
                            val owner = ClassName.fromInternal(reader.className).topLevelClass()
                            val collector = SignatureCollector()
                            reader.accept(collector, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                            result.getOrPut(owner) { mutableSetOf() } += collector.types - owner
                        } catch (_: UnsupportedBytecodeVersionException) {
                            // skip unsupported bytecode versions
                        }
                    }
            }

        return result
    }
}

private class SignatureCollector : ClassVisitor(Opcodes.ASM9) {

    val types = mutableSetOf<ClassName>()

    override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
        superName?.let { add(it) }
        interfaces?.forEach { add(it) }
    }

    override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
        if (access and Opcodes.ACC_PRIVATE != 0) return null
        addDescriptorType(descriptor)
        return null
    }

    override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
        if (access and Opcodes.ACC_PRIVATE != 0) return null
        Type.getArgumentTypes(descriptor).forEach { addType(it) }
        addType(Type.getReturnType(descriptor))
        exceptions?.forEach { add(it) }
        return null
    }

    private fun addDescriptorType(descriptor: String) = addType(Type.getType(descriptor))

    private fun addType(type: Type) {
        when (type.sort) {
            Type.OBJECT -> add(type.internalName)
            Type.ARRAY -> addType(type.elementType)
            else -> Unit
        }
    }

    private fun add(internalName: String) {
        types += ClassName.fromInternal(internalName).topLevelClass()
    }
}
