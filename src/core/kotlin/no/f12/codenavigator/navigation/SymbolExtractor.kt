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
    val packageName: String,
    val className: String,
    val symbolName: String,
    val kind: SymbolKind,
    val sourceFile: String,
)

object SymbolExtractor {

    private val EXCLUDED_METHODS = setOf(
        "<init>", "<clinit>",
        "toString", "hashCode", "equals",
        "copy",
    )

    private val DATA_CLASS_COMPONENT = Regex("""^component\d+$""")
    private val KOTLIN_ACCESSOR = Regex("""^(get|set|is)[A-Z]""")
    private val LAMBDA_METHOD = Regex("""\${'$'}lambda\${'$'}""")
    private val SYNTHETIC_PREFIX = "access$"
    private val EXCLUDED_FIELDS = setOf("INSTANCE")

    fun extract(classFile: File): List<SymbolInfo> {
        val reader = createClassReader(classFile)
        val symbols = mutableListOf<SymbolInfo>()
        var internalName = ""
        var sourceFile: String? = null
        val fieldNames = mutableSetOf<String>()

        fun buildSymbol(name: String, kind: SymbolKind) = SymbolInfo(
            packageName = internalName.substringBeforeLast('/', "").replace('/', '.'),
            className = internalName.substringAfterLast('/').substringBefore('$'),
            symbolName = name,
            kind = kind,
            sourceFile = sourceFile ?: "<unknown>",
        )

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
        if (name in EXCLUDED_METHODS) return true
        if (name.startsWith(SYNTHETIC_PREFIX)) return true
        if (access and Opcodes.ACC_SYNTHETIC != 0) return true
        if (DATA_CLASS_COMPONENT.matches(name)) return true
        if (name.startsWith("copy$")) return true
        if (LAMBDA_METHOD.containsMatchIn(name)) return true
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
