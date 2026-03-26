package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

data class UsageSite(
    val callerClass: ClassName,
    val callerMethod: String,
    val sourceFile: String,
    val targetOwner: ClassName,
    val targetName: String,
    val targetDescriptor: String,
    val kind: UsageKind,
)

enum class UsageKind {
    METHOD_CALL,
    FIELD_ACCESS,
    TYPE_REFERENCE,
}

object UsageScanner {
    fun filterOutsidePackage(usages: List<UsageSite>, outsidePackage: String?): List<UsageSite> {
        if (outsidePackage == null) return usages
        val prefix = PackageName(if (outsidePackage.endsWith(".")) outsidePackage else "$outsidePackage.")
        return usages.filter { !it.callerClass.startsWith(prefix) }
    }

    fun scan(
        classDirectories: List<File>,
        ownerClass: String? = null,
        method: String? = null,
        field: String? = null,
        type: String? = null,
    ): ScanResult<List<UsageSite>> {
        val usages = mutableSetOf<UsageSite>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()
        val ownerRegex = ownerClass?.let { Regex(it, RegexOption.IGNORE_CASE) }
        val typeRegex = type?.let { Regex(it, RegexOption.IGNORE_CASE) }

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            extractUsages(classFile, ownerRegex, method, field, type, typeRegex, usages)
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(usages.toList(), skipped)
    }

    private fun extractUsages(
        classFile: File,
        ownerRegex: Regex?,
        method: String?,
        field: String?,
        type: String?,
        typeRegex: Regex?,
        usages: MutableCollection<UsageSite>,
    ) {
        val reader = createClassReader(classFile)
        var callerClass = ClassName("")
        var sourceFile = "<unknown>"

        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(
                    version: Int, access: Int, name: String,
                    signature: String?, superName: String?, interfaces: Array<out String>?,
                ) {
                    callerClass = ClassName.fromInternal(name)
                }

                override fun visitSource(source: String?, debug: String?) {
                    if (source != null) sourceFile = source
                }

                override fun visitField(
                    access: Int, name: String, descriptor: String,
                    signature: String?, value: Any?,
                ): FieldVisitor? {
                    if (typeRegex != null) {
                        val referencedTypes = extractTypesFromDescriptor(descriptor)
                        if (referencedTypes.any { it.matches(typeRegex) }) {
                            usages.add(
                                UsageSite(
                                    callerClass = callerClass,
                                    callerMethod = "<field>",
                                    sourceFile = sourceFile,
                                    targetOwner = callerClass,
                                    targetName = name,
                                    targetDescriptor = descriptor,
                                    kind = UsageKind.TYPE_REFERENCE,
                                )
                            )
                        }
                    }
                    return null
                }

                override fun visitMethod(
                    access: Int, name: String, descriptor: String,
                    signature: String?, exceptions: Array<out String>?,
                ): MethodVisitor {
                    val callerMethod = name

                    if (typeRegex != null) {
                        val referencedTypes = extractTypesFromDescriptor(descriptor)
                        if (referencedTypes.any { it.matches(typeRegex) }) {
                            usages.add(
                                UsageSite(
                                    callerClass = callerClass,
                                    callerMethod = callerMethod,
                                    sourceFile = sourceFile,
                                    targetOwner = ClassName(type!!),
                                    targetName = callerMethod,
                                    targetDescriptor = descriptor,
                                    kind = UsageKind.TYPE_REFERENCE,
                                )
                            )
                        }
                    }

                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(
                            opcode: Int, instrOwner: String, instrName: String,
                            instrDescriptor: String, isInterface: Boolean,
                        ) {
                            val instrOwnerClass = ClassName.fromInternal(instrOwner)
                            val ownerMatched = field == null && matchesOwner(instrOwnerClass, ownerRegex) && matchesMethod(instrName, method)
                            val fieldMatched = field != null && matchesOwner(instrOwnerClass, ownerRegex) && matchesFieldAccessor(instrName, field)
                            val typeMatched = typeRegex != null && instrOwnerClass.matches(typeRegex) && matchesMethod(instrName, method)
                            if (ownerMatched || fieldMatched || typeMatched) {
                                usages.add(
                                    UsageSite(
                                        callerClass = callerClass,
                                        callerMethod = callerMethod,
                                        sourceFile = sourceFile,
                                        targetOwner = instrOwnerClass,
                                        targetName = instrName,
                                        targetDescriptor = instrDescriptor,
                                        kind = UsageKind.METHOD_CALL,
                                    )
                                )
                            }
                        }

                        override fun visitFieldInsn(
                            opcode: Int, instrOwner: String, instrName: String,
                            instrDescriptor: String,
                        ) {
                            val instrOwnerClass = ClassName.fromInternal(instrOwner)
                            val ownerMatched = field == null && matchesOwner(instrOwnerClass, ownerRegex) && matchesMethod(instrName, method)
                            val fieldMatched = field != null && matchesOwner(instrOwnerClass, ownerRegex) && instrName == field
                            val typeMatched = typeRegex != null && instrOwnerClass.matches(typeRegex) && matchesMethod(instrName, method)
                            if (ownerMatched || fieldMatched || typeMatched) {
                                usages.add(
                                    UsageSite(
                                        callerClass = callerClass,
                                        callerMethod = callerMethod,
                                        sourceFile = sourceFile,
                                        targetOwner = instrOwnerClass,
                                        targetName = instrName,
                                        targetDescriptor = instrDescriptor,
                                        kind = UsageKind.FIELD_ACCESS,
                                    )
                                )
                            }
                        }

                        override fun visitTypeInsn(opcode: Int, instrType: String) {
                            val instrTypeClass = ClassName.fromInternal(instrType)
                            if (typeRegex != null && instrTypeClass.matches(typeRegex)) {
                                usages.add(
                                    UsageSite(
                                        callerClass = callerClass,
                                        callerMethod = callerMethod,
                                        sourceFile = sourceFile,
                                        targetOwner = instrTypeClass,
                                        targetName = typeInsnName(opcode),
                                        targetDescriptor = "",
                                        kind = UsageKind.TYPE_REFERENCE,
                                    )
                                )
                            }
                        }
                    }
                }
            },
            ClassReader.SKIP_FRAMES,
        )
    }

    private fun matchesOwner(actual: ClassName, filter: Regex?): Boolean {
        if (filter == null) return false
        return actual.matches(filter)
    }

    private fun matchesMethod(actual: String, filter: String?): Boolean {
        if (filter == null) return true
        return actual == filter
    }

    private fun matchesFieldAccessor(methodName: String, fieldName: String): Boolean {
        val capitalized = fieldName.replaceFirstChar { it.uppercase() }
        return methodName == "get$capitalized" ||
            methodName == "set$capitalized" ||
            methodName == "is$capitalized"
    }

    private fun extractTypesFromDescriptor(descriptor: String): List<ClassName> {
        val type = runCatching { Type.getType(descriptor) }.getOrNull() ?: return emptyList()
        val types = mutableListOf<ClassName>()
        when (type.sort) {
            Type.METHOD -> {
                collectType(type.returnType, types)
                type.argumentTypes.forEach { collectType(it, types) }
            }
            else -> collectType(type, types)
        }
        return types
    }

    private fun collectType(type: Type, into: MutableList<ClassName>) {
        when (type.sort) {
            Type.OBJECT -> into.add(ClassName.fromInternal(type.internalName))
            Type.ARRAY -> collectType(type.elementType, into)
        }
    }

    private fun typeInsnName(opcode: Int): String = when (opcode) {
        Opcodes.NEW -> "new"
        Opcodes.CHECKCAST -> "checkcast"
        Opcodes.INSTANCEOF -> "instanceof"
        Opcodes.ANEWARRAY -> "newarray"
        else -> "type-ref"
    }
}
