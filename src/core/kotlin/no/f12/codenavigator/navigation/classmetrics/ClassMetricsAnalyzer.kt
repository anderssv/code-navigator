package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.bytecode.ScanResult
import no.f12.codenavigator.navigation.bytecode.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.bytecode.createClassReader
import no.f12.codenavigator.navigation.types.ClassName
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File

private data class ClassMeta(
    val className: ClassName,
    val isInterface: Boolean,
    val reader: ClassReader,
)

object ClassMetricsAnalyzer {

    fun analyze(classDirectories: List<File>): ScanResult<List<ClassMetricsResult>> {
        val classFiles = classDirectories
            .filter { it.exists() }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList() }

        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()
        val superclassOf = mutableMapOf<ClassName, ClassName?>()
        val metadata = mutableListOf<ClassMeta>()

        for (file in classFiles) {
            try {
                val reader = createClassReader(file)
                var className = ClassName("")
                var isInterface = false
                var superName: String? = null

                reader.accept(
                    object : ClassVisitor(Opcodes.ASM9) {
                        override fun visit(
                            version: Int,
                            access: Int,
                            name: String,
                            signature: String?,
                            sn: String?,
                            interfaces: Array<out String>?,
                        ) {
                            className = ClassName.fromInternal(name)
                            isInterface = access and Opcodes.ACC_INTERFACE != 0
                            superName = sn
                        }
                    },
                    ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG,
                )

                superclassOf[className] = superName?.let {
                    if (it == "java/lang/Object") null else ClassName.fromInternal(it)
                }
                metadata += ClassMeta(className, isInterface, reader)
            } catch (e: UnsupportedBytecodeVersionException) {
                skipped += e
            }
        }

        val results = metadata
            .filter { !it.isInterface && !it.className.isGenerated() }
            .map { meta ->
                val fieldAccessData = FieldAccessAnalyzer.analyze(meta.reader)
                val cohesion = CohesionGraphBuilder.build(fieldAccessData.fieldAccessByMethod)
                val wmc = MethodComplexityAnalyzer.analyze(meta.reader, fieldAccessData.fields)
                val cbo = TypeCouplingAnalyzer.analyze(meta.reader, meta.className)
                val dit = computeDit(meta.className, superclassOf)

                ClassMetricsResult(
                    className = meta.className,
                    packageName = meta.className.packageName(),
                    totalMethods = cohesion.totalMethods,
                    tcc = cohesion.tcc,
                    lcc = cohesion.lcc,
                    verdict = cohesion.verdict,
                    wmc = wmc,
                    cbo = cbo,
                    dit = dit,
                )
            }
            .sortedBy { it.className.value }

        return ScanResult(results, skipped)
    }

    /** Depth of the superclass chain. Stops at java.lang.Object, or at the first ancestor whose bytecode wasn't scanned (e.g. an external framework class) — undercounts external hierarchies, which is an accepted limitation pending classpath JAR resolution. */
    private fun computeDit(className: ClassName, superclassOf: Map<ClassName, ClassName?>): Int {
        var current = superclassOf[className]
        var depth = 0
        val seen = mutableSetOf<ClassName>()
        while (current != null && current !in seen) {
            depth++
            seen += current
            current = superclassOf[current]
        }
        return depth
    }
}
