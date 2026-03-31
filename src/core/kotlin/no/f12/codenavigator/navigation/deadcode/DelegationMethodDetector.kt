package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.createClassReader
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.signature
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

/**
 * Scans compiled Kotlin class files for delegation-generated methods.
 *
 * Kotlin delegation (e.g. `class Foo(val m: Map<K,V>) : Map<K,V> by m`)
 * generates forwarding methods in bytecode that don't appear in Kotlin
 * metadata. This detector finds them by comparing bytecode methods against
 * metadata-declared functions, filtering out bridge/synthetic/constructor
 * methods that are already handled elsewhere.
 *
 * Returns a set of [MethodRef]s representing delegation methods. Dead code
 * analysis uses this to filter out delegation methods, which are compiler-
 * generated and not meaningful dead code candidates.
 */
object DelegationMethodDetector {

    private const val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"

    fun scanAll(classDirectories: List<File>): Set<MethodRef> {
        val result = mutableSetOf<MethodRef>()

        for (dir in classDirectories) {
            if (!dir.exists()) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { classFile ->
                    try {
                        result.addAll(detect(classFile))
                    } catch (_: Exception) {
                        // Skip files we can't read
                    }
                }
        }

        return result
    }

    private fun detect(classFile: File): Set<MethodRef> {
        val reader = createClassReader(classFile)
        var className = ClassName("")
        var metadataKind = 0
        var metadataVersion: IntArray? = null
        var data1: Array<String>? = null
        var data2: Array<String>? = null
        val bytecodeMethods = mutableListOf<Pair<String, Int>>() // name, access

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

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    bytecodeMethods.add(name to access)
                    return null
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

        val metadataFunctions = when (val parsed = KotlinClassMetadata.readStrict(metadata)) {
            is KotlinClassMetadata.Class ->
                parsed.kmClass.functions.mapNotNull { fn -> fn.signature?.name }.toSet()
            else -> return emptySet()
        }

        return bytecodeMethods
            .filter { (name, access) ->
                name !in metadataFunctions &&
                    access and Opcodes.ACC_BRIDGE == 0 &&
                    access and Opcodes.ACC_SYNTHETIC == 0 &&
                    name != "<init>" &&
                    name != "<clinit>"
            }
            .map { (name, _) -> MethodRef(className, name) }
            .toSet()
    }
}
