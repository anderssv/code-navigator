package no.f12.codenavigator.navigation.relations.hierarchy

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.relations.implementors.ImplementorInfo
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.bytecode.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.bytecode.createClassReader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile

data class SupertypeInfo(
    val className: ClassName,
    val kind: SupertypeKind,
    val supertypes: List<SupertypeInfo>,
)

enum class SupertypeKind { CLASS, INTERFACE }

data class TypeHierarchyResult(
    val className: ClassName,
    val sourceFile: String,
    val supertypes: List<SupertypeInfo>,
    val implementors: List<ImplementorInfo>,
)

object TypeHierarchyBuilder {

    fun build(
        classDirectories: List<File>,
        pattern: String,
        projectOnly: Boolean,
        classpath: List<Path> = emptyList(),
    ): List<TypeHierarchyResult> {
        val classIndex = scanAllClasses(classDirectories)
        val classpathIndex = if (!projectOnly) scanClasspath(classpath) else emptyMap()
        val fullIndex = classpathIndex + classIndex // project classes take precedence
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val matchingClasses = classIndex.filter { it.key.matches(regex) }

        val interfaceRegistry = InterfaceRegistry.build(classDirectories).data

        return matchingClasses.map { (className, info) ->
            TypeHierarchyResult(
                className = className,
                sourceFile = info.sourceFile,
                supertypes = resolveSupertypes(info, fullIndex, mutableSetOf()),
                implementors = interfaceRegistry.implementorsOf(className),
            )
        }.sortedBy { it.className }
    }

    private fun resolveSupertypes(
        info: ClassIndexEntry,
        classIndex: Map<ClassName, ClassIndexEntry>,
        visited: MutableSet<ClassName>,
    ): List<SupertypeInfo> {
        val result = mutableListOf<SupertypeInfo>()

        info.superClass?.let { superClass ->
            if (superClass !in visited) {
                visited.add(superClass)
                val superEntry = classIndex[superClass]
                val childSupertypes = if (superEntry != null) {
                    resolveSupertypes(superEntry, classIndex, visited)
                } else {
                    emptyList()
                }
                result.add(SupertypeInfo(superClass, SupertypeKind.CLASS, childSupertypes))
            }
        }

        for (iface in info.interfaces) {
            if (iface !in visited) {
                visited.add(iface)
                val ifaceEntry = classIndex[iface]
                val childSupertypes = if (ifaceEntry != null) {
                    resolveSupertypes(ifaceEntry, classIndex, visited)
                } else {
                    emptyList()
                }
                result.add(SupertypeInfo(iface, SupertypeKind.INTERFACE, childSupertypes))
            }
        }

        return result
    }

    private fun scanAllClasses(classDirectories: List<File>): Map<ClassName, ClassIndexEntry> {
        val index = mutableMapOf<ClassName, ClassIndexEntry>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            val entry = extractClassEntry(classFile)
                            if (!entry.className.isSynthetic()) {
                                index[entry.className] = entry
                            }
                        } catch (_: UnsupportedBytecodeVersionException) {
                            // skip
                        }
                    }
            }

        return index
    }

    private fun scanClasspath(classpath: List<Path>): Map<ClassName, ClassIndexEntry> {
        val index = mutableMapOf<ClassName, ClassIndexEntry>()

        for (path in classpath) {
            val file = path.toFile()
            if (!file.exists()) continue

            if (file.isDirectory) {
                file.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            val entry = extractClassEntry(classFile)
                            index.putIfAbsent(entry.className, entry)
                        } catch (_: Exception) {
                            // skip unreadable entries
                        }
                    }
            } else if (file.extension == "jar") {
                try {
                    JarFile(file).use { jar ->
                        for (entry in jar.entries()) {
                            if (!entry.name.endsWith(".class")) continue
                            try {
                                val bytes = jar.getInputStream(entry).readBytes()
                                val classEntry = extractClassEntryFromBytes(bytes)
                                index.putIfAbsent(classEntry.className, classEntry)
                            } catch (_: Exception) {
                                // skip unreadable entries
                            }
                        }
                    }
                } catch (_: Exception) {
                    // skip unreadable JARs
                }
            }
        }

        return index
    }

    private fun extractClassEntryFromBytes(bytes: ByteArray): ClassIndexEntry {
        val reader = ClassReader(bytes)
        var className = ClassName("")
        var sourceFile = "<unknown>"
        var superClass: ClassName? = null
        var interfaces = emptyList<ClassName>()

        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaceNames: Array<out String>?,
                ) {
                    className = ClassName.fromInternal(name)
                    superClass = superName
                        ?.takeIf { it != "java/lang/Object" }
                        ?.let { ClassName.fromInternal(it) }
                    interfaces = interfaceNames
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

        return ClassIndexEntry(className, sourceFile, superClass, interfaces)
    }

    private fun extractClassEntry(classFile: File): ClassIndexEntry {
        val reader = createClassReader(classFile)
        var className = ClassName("")
        var sourceFile = "<unknown>"
        var superClass: ClassName? = null
        var interfaces = emptyList<ClassName>()

        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaceNames: Array<out String>?,
                ) {
                    className = ClassName.fromInternal(name)
                    superClass = superName
                        ?.takeIf { it != "java/lang/Object" }
                        ?.let { ClassName.fromInternal(it) }
                    interfaces = interfaceNames
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

        return ClassIndexEntry(className, sourceFile, superClass, interfaces)
    }
}

data class ClassIndexEntry(
    val className: ClassName,
    val sourceFile: String,
    val superClass: ClassName?,
    val interfaces: List<ClassName>,
)
