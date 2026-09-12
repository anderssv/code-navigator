package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.navigation.bytecode.ScanResult
import no.f12.codenavigator.navigation.bytecode.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.bytecode.createClassReader
import no.f12.codenavigator.navigation.types.ClassName
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

/**
 * Where in a class the ambient read happens. [INITIALIZER] covers `<init>`/`<clinit>` and Kotlin's
 * `$default` bridges, which is where a default constructor argument (`val at: Instant = Instant.now()`)
 * lands. Those are genuine findings but are reported separately: they're the most common hit by a
 * wide margin and the cheapest to fix, so mixing them into the method-body list buries the rest.
 */
enum class AmbientSite {
    METHOD_BODY,
    INITIALIZER,
}

data class AmbientCall(
    val callerClass: ClassName,
    val callerMethod: String,
    val signal: AmbientSignal,
    val descriptor: String,
    val sourceFile: String?,
    val line: Int?,
    val site: AmbientSite,
) {
    val category: AmbientCategory get() = signal.category

    /** Where to look in source. Inner classes and lambdas report against their top-level owner. */
    fun location(): String {
        val file = sourceFile ?: callerClass.topLevelClass().simpleName()
        return if (line != null) "$file:$line" else file
    }
}

/**
 * [clockInjectingClasses] is the project's own evidence that injecting a clock is the established
 * convention here — classes holding a `Clock` field or taking one as a constructor parameter. Used
 * to decide whether a violation is drift from the project's own practice or simply a project that
 * has never injected a clock anywhere (in which case the finding is advisory, not a violation).
 */
data class AmbientScan(
    val calls: List<AmbientCall>,
    val clockInjectingClasses: Set<ClassName>,
)

object AmbientCallScanner {

    private val CLOCK_TYPE_MARKERS = listOf(
        "Ljava/time/Clock;",
        "Lkotlinx/datetime/Clock;",
        "Lkotlin/time/Clock;",
    )

    fun scan(classDirectories: List<File>): ScanResult<AmbientScan> {
        val calls = mutableListOf<AmbientCall>()
        val clockInjecting = mutableSetOf<ClassName>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            scanClass(classFile, calls, clockInjecting)
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(AmbientScan(calls, clockInjecting), skipped)
    }

    private fun scanClass(
        classFile: File,
        calls: MutableList<AmbientCall>,
        clockInjecting: MutableSet<ClassName>,
    ) {
        val reader = createClassReader(classFile)
        var owner = ClassName("")
        var sourceFile: String? = null

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
                    owner = ClassName.fromInternal(name)
                }

                override fun visitSource(source: String?, debug: String?) {
                    sourceFile = source
                }

                override fun visitField(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    value: Any?,
                ): org.objectweb.asm.FieldVisitor? {
                    if (CLOCK_TYPE_MARKERS.any { descriptor.contains(it) }) {
                        clockInjecting.add(owner.topLevelClass())
                    }
                    return null
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    methodDescriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    if (name == "<init>" && CLOCK_TYPE_MARKERS.any { methodDescriptor.contains(it) }) {
                        clockInjecting.add(owner.topLevelClass())
                    }

                    val site = if (isInitializer(name)) AmbientSite.INITIALIZER else AmbientSite.METHOD_BODY

                    return object : MethodVisitor(Opcodes.ASM9) {
                        private var currentLine: Int? = null

                        override fun visitLineNumber(line: Int, start: Label) {
                            currentLine = line
                        }

                        override fun visitMethodInsn(
                            opcode: Int,
                            callOwner: String,
                            callName: String,
                            callDescriptor: String,
                            isInterface: Boolean,
                        ) {
                            val signal = AmbientBlocklist.violatedBy(callOwner, callName, callDescriptor) ?: return
                            calls.add(
                                AmbientCall(
                                    callerClass = owner,
                                    callerMethod = name,
                                    signal = signal,
                                    descriptor = callDescriptor,
                                    sourceFile = sourceFile,
                                    line = currentLine,
                                    site = site,
                                )
                            )
                        }
                    }
                }
            },
            ClassReader.SKIP_FRAMES,
        )
    }

    private fun isInitializer(methodName: String): Boolean =
        methodName == "<init>" || methodName == "<clinit>" || methodName.endsWith("\$default")
}
