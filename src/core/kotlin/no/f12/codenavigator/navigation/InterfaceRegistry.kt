package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File

data class ImplementorInfo(
    val className: ClassName,
    val sourceFile: String,
)

class InterfaceRegistry(
    private val interfaceToImplementors: Map<ClassName, List<ImplementorInfo>>,
) {
    fun implementorsOf(interfaceName: ClassName): List<ImplementorInfo> =
        interfaceToImplementors[interfaceName] ?: emptyList()

    fun findInterfaces(pattern: String): List<ClassName> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return interfaceToImplementors.keys
            .filter { it.matches(regex) }
            .sortedBy { it.toString() }
    }

    fun forEachEntry(action: (interfaceName: ClassName, implementors: List<ImplementorInfo>) -> Unit) {
        interfaceToImplementors.forEach { (iface, impls) -> action(iface, impls) }
    }

    companion object {
        fun build(classDirectories: List<File>): ScanResult<InterfaceRegistry> {
            val map = mutableMapOf<ClassName, MutableList<ImplementorInfo>>()
            val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

            classDirectories
                .filter { it.exists() }
                .forEach { dir ->
                    dir.walkTopDown()
                        .filter { it.isFile && it.extension == "class" }
                        .forEach { classFile ->
                            try {
                                extractInterfaces(classFile, map)
                            } catch (e: UnsupportedBytecodeVersionException) {
                                skipped.add(e)
                            }
                        }
                }

            val sorted = map.mapValues { (_, impls) -> impls.sortedBy { it.className.toString() } }
            return ScanResult(
                data = InterfaceRegistry(sorted),
                skippedFiles = skipped,
            )
        }

        private fun extractInterfaces(
            classFile: File,
            map: MutableMap<ClassName, MutableList<ImplementorInfo>>,
        ) {
            val reader = createClassReader(classFile)
            var className = ClassName("")
            var sourceFile = "<unknown>"
            var implementedInterfaces = emptyList<ClassName>()

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
                        implementedInterfaces = interfaces
                            ?.map { ClassName.fromInternal(it) }
                            ?: emptyList()
                    }

                    override fun visitSource(source: String?, debug: String?) {
                        if (source != null) {
                            sourceFile = source
                        }
                    }
                },
                ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
            )

            if (className.isSynthetic() || implementedInterfaces.isEmpty()) return

            val info = ImplementorInfo(className, sourceFile)
            implementedInterfaces.forEach { ifaceName ->
                map.getOrPut(ifaceName) { mutableListOf() }.add(info)
            }
        }
    }
}
