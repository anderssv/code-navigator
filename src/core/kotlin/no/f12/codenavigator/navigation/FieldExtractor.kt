package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import java.io.File

/**
 * Lightweight scanner that extracts field names per class from bytecode.
 * Used by dead code analysis to filter out Kotlin property accessors
 * (getName/setName/isActive) that correspond to declared fields.
 */
object FieldExtractor {

    fun scanAll(classDirectories: List<File>): Map<ClassName, Set<String>> {
        val result = mutableMapOf<ClassName, MutableSet<String>>()

        for (dir in classDirectories) {
            dir.walk()
                .filter { it.isFile && it.extension == "class" }
                .forEach { classFile ->
                    try {
                        val (className, fields) = extract(classFile)
                        if (fields.isNotEmpty()) {
                            result.getOrPut(className) { mutableSetOf() }.addAll(fields)
                        }
                    } catch (_: UnsupportedBytecodeVersionException) {
                        // Skip files we can't read
                    }
                }
        }

        return result
    }

    private fun extract(classFile: File): Pair<ClassName, Set<String>> {
        val reader = createClassReader(classFile)
        var className = ClassName("")
        val fields = mutableSetOf<String>()

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

                override fun visitField(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    value: Any?,
                ): FieldVisitor? {
                    if (name !in KotlinMethodFilter.EXCLUDED_FIELDS) {
                        fields.add(name)
                    }
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
        )

        return className to fields
    }
}
