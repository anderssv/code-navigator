package no.f12.codenavigator.navigation.symbol

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.KotlinMethodFilter
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.core.createClassReader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

enum class SymbolKind {
    METHOD,
    FIELD,
}

data class SymbolInfo(
    val packageName: PackageName,
    val className: ClassName,
    val symbolName: String,
    val kind: SymbolKind,
    val sourceFile: String,
)

object SymbolExtractor {

    fun extract(classFile: File): List<SymbolInfo> {
        val reader = createClassReader(classFile)
        val symbols = mutableListOf<SymbolInfo>()
        var internalName = ""
        var sourceFile: String? = null
        val fieldNames = mutableSetOf<String>()

        fun buildSymbol(name: String, kind: SymbolKind): SymbolInfo {
            val fullClassName = ClassName.fromInternal(internalName)
            return SymbolInfo(
                packageName = fullClassName.packageName(),
                className = fullClassName,
                symbolName = name,
                kind = kind,
                sourceFile = sourceFile ?: "<unknown>",
            )
        }

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
                    internalName = name
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
                ): FieldVisitor? {
                    if (access and Opcodes.ACC_SYNTHETIC != 0) return null
                    if (name in KotlinMethodFilter.EXCLUDED_FIELDS) return null

                    fieldNames.add(name)
                    symbols.add(buildSymbol(name, SymbolKind.FIELD))
                    return null
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (KotlinMethodFilter.isExcludedMethod(name, access)) return null
                    symbols.add(buildSymbol(name, SymbolKind.METHOD))
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG.inv() or ClassReader.SKIP_FRAMES,
        )

        return symbols.filter { symbol ->
            !(symbol.kind == SymbolKind.METHOD && KotlinMethodFilter.isAccessorForField(symbol.symbolName, fieldNames))
        }
    }
}
