package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.FileCache
import no.f12.codenavigator.navigation.core.ScanResult
import java.io.File

object ClassIndexCache : FileCache<List<ClassInfo>>() {

    override fun write(cacheFile: File, data: List<ClassInfo>) {
        writeLines(cacheFile) { writer ->
            data.forEach { info ->
                writer.write(
                    listOf(
                        info.className.toString(),
                        info.sourceFileName,
                        info.reconstructedSourcePath,
                        info.isUserDefinedClass.toString(),
                    ).joinToString(FIELD_SEPARATOR),
                )
                writer.newLine()
            }
        }
    }

    override fun read(cacheFile: File): List<ClassInfo> =
        readLines(cacheFile) { parts ->
            ClassInfo(
                className = ClassName(parts[0]),
                sourceFileName = parts[1],
                reconstructedSourcePath = parts[2],
                isUserDefinedClass = parts[3].toBoolean(),
            )
        }

    override fun build(classDirectories: List<File>): ScanResult<List<ClassInfo>> =
        ClassScanner.scan(classDirectories)
}
