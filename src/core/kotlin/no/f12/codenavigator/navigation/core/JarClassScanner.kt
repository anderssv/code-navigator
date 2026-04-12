package no.f12.codenavigator.navigation.core

import java.io.File
import java.util.jar.JarFile

data class JarClassEntry(
    val entryName: String,
    val bytes: ByteArray,
    val label: String,
)

object JarClassScanner {

    fun scan(jarFile: File): List<JarClassEntry> {
        val entries = mutableListOf<JarClassEntry>()

        JarFile(jarFile).use { jar ->
            for (entry in jar.entries()) {
                if (entry.isDirectory) continue
                if (!entry.name.endsWith(".class")) continue

                val bytes = jar.getInputStream(entry).readBytes()
                entries.add(
                    JarClassEntry(
                        entryName = entry.name,
                        bytes = bytes,
                        label = "${jarFile.name}!/${entry.name}",
                    ),
                )
            }
        }

        return entries
    }
}
