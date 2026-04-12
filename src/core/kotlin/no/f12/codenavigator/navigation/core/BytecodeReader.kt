package no.f12.codenavigator.navigation.core

import org.objectweb.asm.ClassReader
import java.io.File

class UnsupportedBytecodeVersionException(message: String) : RuntimeException(message)

data class ScanResult<T>(
    val data: T,
    val skippedFiles: List<UnsupportedBytecodeVersionException>,
)

fun createClassReader(bytes: ByteArray, label: String = "<bytes>"): ClassReader =
    try {
        ClassReader(bytes)
    } catch (e: IllegalArgumentException) {
        val majorVersion = extractMajorVersion(bytes)
        val javaVersion = majorVersion?.let { it - 44 }
        val versionHint = if (javaVersion != null) " (Java $javaVersion)" else ""
        throw UnsupportedBytecodeVersionException(
            "Cannot read $label: unsupported bytecode version${versionHint}. " +
                "This usually means your project targets a newer JVM than the code-navigator plugin supports. " +
                "Try upgrading the code-navigator plugin."
        )
    }

fun createClassReader(classFile: File): ClassReader =
    createClassReader(classFile.readBytes(), classFile.name)

private fun extractMajorVersion(bytes: ByteArray): Int? =
    if (bytes.size >= 8) {
        ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
    } else {
        null
    }
