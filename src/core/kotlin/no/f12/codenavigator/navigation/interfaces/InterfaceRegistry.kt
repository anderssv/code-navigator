package no.f12.codenavigator.navigation.interfaces

import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.ScanResult
import no.f12.codenavigator.navigation.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.createClassReader

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
    private val classToInterfaces: Map<ClassName, Set<ClassName>> by lazy {
        val result = mutableMapOf<ClassName, MutableSet<ClassName>>()
        interfaceToImplementors.forEach { (interfaceName, implementors) ->
            for (impl in implementors) {
                result.getOrPut(impl.className) { mutableSetOf() }.add(interfaceName)
            }
        }
        result
    }

    fun implementorsOf(interfaceName: ClassName): List<ImplementorInfo> =
        interfaceToImplementors[interfaceName] ?: emptyList()

    fun interfacesOf(className: ClassName): Set<ClassName> =
        classToInterfaces[className] ?: emptySet()

    fun findInterfaces(pattern: String): List<ClassName> {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return interfaceToImplementors.keys
            .filter { it.matches(regex) }
            .sortedBy { it.toString() }
    }

    fun forEachEntry(action: (interfaceName: ClassName, implementors: List<ImplementorInfo>) -> Unit) {
        interfaceToImplementors.forEach { (iface, impls) -> action(iface, impls) }
    }

    fun filteredByImplementor(predicate: (ClassName) -> Boolean): InterfaceRegistry {
        val filteredMap = interfaceToImplementors.mapValues { (_, impls) ->
            impls.filter { predicate(it.className) }
        }.filterValues { it.isNotEmpty() }
        return InterfaceRegistry(filteredMap)
    }

    fun implementorMap(): Map<ClassName, Set<ClassName>> =
        interfaceToImplementors.mapValues { (_, impls) -> impls.map { it.className }.toSet() }

    fun classToInterfacesMap(): Map<ClassName, Set<ClassName>> = classToInterfaces

    fun externalInterfacesOf(projectClasses: Set<ClassName>): Map<ClassName, Set<ClassName>> {
        val result = mutableMapOf<ClassName, MutableSet<ClassName>>()
        interfaceToImplementors.forEach { (interfaceName, implementors) ->
            if (interfaceName !in projectClasses) {
                for (impl in implementors) {
                    if (impl.className in projectClasses) {
                        result.getOrPut(impl.className) { mutableSetOf() }.add(interfaceName)
                    }
                }
            }
        }
        return result
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
            var superClassName: ClassName? = null

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
                        superClassName = superName
                            ?.takeIf { it != "java/lang/Object" }
                            ?.let { ClassName.fromInternal(it) }
                    }

                    override fun visitSource(source: String?, debug: String?) {
                        if (source != null) {
                            sourceFile = source
                        }
                    }
                },
                ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
            )

            if (className.isSynthetic()) return

            val supertypes = buildList {
                addAll(implementedInterfaces)
                superClassName?.let { add(it) }
            }
            if (supertypes.isEmpty()) return

            val info = ImplementorInfo(className, sourceFile)
            supertypes.forEach { supertypeName ->
                map.getOrPut(supertypeName) { mutableListOf() }.add(info)
            }
        }
    }
}
