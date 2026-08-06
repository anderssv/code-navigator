package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.bytecode.createClassReader
import kotlin.metadata.KmClass
import kotlin.metadata.isConst
import kotlin.metadata.jvm.KotlinClassMetadata
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File

/**
 * Scans compiled Kotlin class files for classes/objects composed entirely of
 * `const val` declarations (and no functions), by parsing the `@kotlin.Metadata`
 * annotation embedded in bytecode.
 *
 * Returns a set of [ClassName]s representing "const val holders". Dead code
 * analysis uses this to avoid a HIGH-confidence false positive: Kotlin inlines
 * `const val` references as literal values at every call site, so no bytecode
 * edge (`GETSTATIC` or equivalent) ever points back to the declaring class,
 * even when the holder is referenced extensively in source.
 */
object ConstValHolderDetector {

    private const val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"

    fun scanAll(classDirectories: List<File>): Set<ClassName> {
        val result = mutableSetOf<ClassName>()

        for (dir in classDirectories) {
            if (!dir.exists()) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { classFile ->
                    try {
                        detectConstValHolder(classFile)?.let { result.add(it) }
                    } catch (_: Exception) {
                        // Skip files we can't read
                    }
                }
        }

        return result
    }

    private fun detectConstValHolder(classFile: File): ClassName? {
        val reader = createClassReader(classFile)
        var className = ClassName("")
        var metadataKind = 0
        var metadataVersion: IntArray? = null
        var data1: Array<String>? = null
        var data2: Array<String>? = null

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

                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                    if (descriptor != KOTLIN_METADATA_DESC) return null
                    return object : AnnotationVisitor(Opcodes.ASM9) {
                        override fun visit(name: String, value: Any) {
                            when (name) {
                                "k" -> metadataKind = value as Int
                                "mv" -> metadataVersion = value as IntArray
                            }
                        }

                        override fun visitArray(name: String): AnnotationVisitor {
                            return object : AnnotationVisitor(Opcodes.ASM9) {
                                private val collected = mutableListOf<Any>()

                                override fun visit(name: String?, value: Any) {
                                    collected.add(value)
                                }

                                override fun visitEnd() {
                                    when (name) {
                                        "d1" -> data1 = collected.filterIsInstance<String>().toTypedArray()
                                        "d2" -> data2 = collected.filterIsInstance<String>().toTypedArray()
                                    }
                                }
                            }
                        }
                    }
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
        )

        val d1 = data1 ?: return null
        val d2 = data2 ?: return null

        val metadata = kotlin.Metadata(
            kind = metadataKind,
            metadataVersion = metadataVersion ?: intArrayOf(),
            data1 = d1,
            data2 = d2,
        )

        return when (val parsed = KotlinClassMetadata.readStrict(metadata)) {
            is KotlinClassMetadata.Class -> if (isConstValHolder(parsed.kmClass)) className else null
            else -> null
        }
    }

    private fun isConstValHolder(kmClass: KmClass): Boolean {
        if (kmClass.properties.isEmpty()) return false
        if (kmClass.functions.isNotEmpty()) return false
        return kmClass.properties.all { it.isConst }
    }
}
