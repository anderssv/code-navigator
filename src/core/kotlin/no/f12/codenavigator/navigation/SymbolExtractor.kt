package no.f12.codenavigator.navigation

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

    private val KOTLIN_ACCESSOR = Regex("""^(get|set|is)[A-Z]""")
    private val EXCLUDED_FIELDS = setOf("INSTANCE")

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
                    if (name in EXCLUDED_FIELDS) return null

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
                    if (isExcludedMethod(name, access)) return null
                    symbols.add(buildSymbol(name, SymbolKind.METHOD))
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG.inv() or ClassReader.SKIP_FRAMES,
        )

        return symbols.filter { symbol ->
            !(symbol.kind == SymbolKind.METHOD && isAccessorForField(symbol.symbolName, fieldNames))
        }
    }

    private fun isExcludedMethod(name: String, access: Int): Boolean {
        if (KotlinMethodFilter.isGenerated(name)) return true
        if (access and Opcodes.ACC_SYNTHETIC != 0) return true
        return false
    }

    private fun isAccessorForField(methodName: String, fieldNames: Set<String>): Boolean {
        if (!KOTLIN_ACCESSOR.containsMatchIn(methodName)) return false

        val prefix = when {
            methodName.startsWith("get") -> "get"
            methodName.startsWith("set") -> "set"
            methodName.startsWith("is") -> "is"
            else -> return false
        }
        val propertyName = methodName.removePrefix(prefix).replaceFirstChar { it.lowercase() }
        return propertyName in fieldNames
    }
}
