package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File

data class ImplementorInfo(
    val className: String,
    val sourceFile: String,
)

class InterfaceRegistry(
    private val interfaceToImplementors: Map<String, List<ImplementorInfo>>,
) {
    fun implementorsOf(interfaceName: String): List<ImplementorInfo> =
        interfaceToImplementors[interfaceName] ?: emptyList()

    fun findInterfaces(pattern: String): List<String> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return interfaceToImplementors.keys
            .filter { regex.containsMatchIn(it) }
            .sorted()
    }

    fun forEachEntry(action: (interfaceName: String, implementors: List<ImplementorInfo>) -> Unit) {
        interfaceToImplementors.forEach { (iface, impls) -> action(iface, impls) }
    }

    companion object {
        private val SYNTHETIC_SUFFIX = Regex("""\$\d+$""")
        private val LAMBDA_PATTERN = Regex("""\${'$'}lambda\${'$'}""")

        fun build(classDirectories: List<File>): ScanResult<InterfaceRegistry> {
            val map = mutableMapOf<String, MutableList<ImplementorInfo>>()
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

            val sorted = map.mapValues { (_, impls) -> impls.sortedBy { it.className } }
            return ScanResult(
                data = InterfaceRegistry(sorted),
                skippedFiles = skipped,
            )
        }

        private fun extractInterfaces(
            classFile: File,
            map: MutableMap<String, MutableList<ImplementorInfo>>,
        ) {
            val reader = createClassReader(classFile)
            var className = ""
            var sourceFile = "<unknown>"
            var implementedInterfaces = emptyList<String>()
            var isSynthetic = false

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
                        className = name.replace('/', '.')
                        isSynthetic = isSyntheticClass(name)
                        implementedInterfaces = interfaces
                            ?.map { it.replace('/', '.') }
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

            if (isSynthetic || implementedInterfaces.isEmpty()) return

            val info = ImplementorInfo(className, sourceFile)
            implementedInterfaces.forEach { ifaceName ->
                map.getOrPut(ifaceName) { mutableListOf() }.add(info)
            }
        }

        private fun isSyntheticClass(internalName: String): Boolean =
            SYNTHETIC_SUFFIX.containsMatchIn(internalName) ||
                LAMBDA_PATTERN.containsMatchIn(internalName)
    }
}
