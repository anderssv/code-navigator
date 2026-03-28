package no.f12.codenavigator.navigation.interfaces

import no.f12.codenavigator.CacheFreshness
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.FileCache
import no.f12.codenavigator.navigation.ScanResult
import java.io.File

object InterfaceRegistryCache : FileCache<InterfaceRegistry>() {

    override fun write(cacheFile: File, data: InterfaceRegistry) {
        CacheFreshness.atomicWrite(cacheFile) { file ->
            file.bufferedWriter().use { writer ->
                data.forEachEntry { interfaceName, implementors ->
                    implementors.forEach { impl ->
                        writer.write(
                            listOf(
                                interfaceName,
                                impl.className,
                                impl.sourceFile,
                            ).joinToString(FIELD_SEPARATOR),
                        )
                        writer.newLine()
                    }
                }
            }
        }
    }

    override fun read(cacheFile: File): InterfaceRegistry {
        val map = mutableMapOf<ClassName, MutableList<ImplementorInfo>>()

        cacheFile.useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split(FIELD_SEPARATOR)
                val interfaceName = ClassName(parts[0])
                val impl = ImplementorInfo(ClassName(parts[1]), parts[2])
                map.getOrPut(interfaceName) { mutableListOf() }.add(impl)
            }
        }

        return InterfaceRegistry(map)
    }

    override fun build(classDirectories: List<File>): ScanResult<InterfaceRegistry> =
        InterfaceRegistry.build(classDirectories)
}
