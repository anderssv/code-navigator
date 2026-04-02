package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.core.createClassReader
import kotlin.metadata.KmClass
import kotlin.metadata.KmPackage
import kotlin.metadata.isInline
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.signature
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File

/**
 * Scans compiled Kotlin class files for `inline` function declarations
 * by parsing the `@kotlin.Metadata` annotation embedded in bytecode.
 *
 * Returns a set of [MethodRef]s representing inline methods. Dead code
 * analysis uses this to filter out inline methods, which leave no call
 * edges in bytecode (the compiler inlines them at each call site).
 */
object InlineMethodDetector {

    private const val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"

    fun scanAll(classDirectories: List<File>): Set<MethodRef> {
        val result = mutableSetOf<MethodRef>()

        for (dir in classDirectories) {
            if (!dir.exists()) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { classFile ->
                    try {
                        result.addAll(detectInline(classFile))
                    } catch (_: Exception) {
                        // Skip files we can't read
                    }
                }
        }

        return result
    }

    private fun detectInline(classFile: File): Set<MethodRef> {
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

        val d1 = data1 ?: return emptySet()
        val d2 = data2 ?: return emptySet()

        val metadata = kotlin.Metadata(
            kind = metadataKind,
            metadataVersion = metadataVersion ?: intArrayOf(),
            data1 = d1,
            data2 = d2,
        )

        return when (val parsed = KotlinClassMetadata.readStrict(metadata)) {
            is KotlinClassMetadata.Class -> extractInlineFromClass(className, parsed.kmClass)
            is KotlinClassMetadata.FileFacade -> extractInlineFromPackage(className, parsed.kmPackage)
            is KotlinClassMetadata.MultiFileClassPart -> extractInlineFromPackage(className, parsed.kmPackage)
            else -> emptySet()
        }
    }

    private fun extractInlineFromClass(className: ClassName, kmClass: KmClass): Set<MethodRef> =
        kmClass.functions
            .filter { it.isInline }
            .mapNotNull { fn -> fn.signature?.toMethodName() }
            .map { MethodRef(className, it) }
            .toSet()

    private fun extractInlineFromPackage(className: ClassName, kmPackage: KmPackage): Set<MethodRef> =
        kmPackage.functions
            .filter { it.isInline }
            .mapNotNull { fn -> fn.signature?.toMethodName() }
            .map { MethodRef(className, it) }
            .toSet()

    private fun JvmMethodSignature.toMethodName(): String = name
}
