package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

data class UsageSite(
    val callerClass: String,
    val callerMethod: String,
    val sourceFile: String,
    val targetOwner: String,
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
    fun scan(
        classDirectories: List<File>,
        owner: String? = null,
        method: String? = null,
        type: String? = null,
    ): ScanResult<List<UsageSite>> {
        val usages = mutableListOf<UsageSite>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        classDirectories
            .filter { it.exists() }
            .forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            extractUsages(classFile, owner, method, type, usages)
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(usages, skipped)
    }

    private fun extractUsages(
        classFile: File,
        owner: String?,
        method: String?,
        type: String?,
        usages: MutableList<UsageSite>,
    ) {
        val reader = createClassReader(classFile)
        var callerClass = ""
        var sourceFile = "<unknown>"

        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(
                    version: Int, access: Int, name: String,
                    signature: String?, superName: String?, interfaces: Array<out String>?,
                ) {
                    callerClass = name.replace('/', '.')
                }

                override fun visitSource(source: String?, debug: String?) {
                    if (source != null) sourceFile = source
                }

                override fun visitField(
                    access: Int, name: String, descriptor: String,
                    signature: String?, value: Any?,
                ): FieldVisitor? {
                    if (type != null) {
                        val referencedTypes = extractTypesFromDescriptor(descriptor)
                        if (referencedTypes.any { matchesType(it, type) }) {
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

                    if (type != null) {
                        val referencedTypes = extractTypesFromDescriptor(descriptor)
                        if (referencedTypes.any { matchesType(it, type) }) {
                            usages.add(
                                UsageSite(
                                    callerClass = callerClass,
                                    callerMethod = callerMethod,
                                    sourceFile = sourceFile,
                                    targetOwner = type,
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
                            val instrOwnerDot = instrOwner.replace('/', '.')
                            if (matchesOwner(instrOwnerDot, owner) && matchesMethod(instrName, method)) {
                                usages.add(
                                    UsageSite(
                                        callerClass = callerClass,
                                        callerMethod = callerMethod,
                                        sourceFile = sourceFile,
                                        targetOwner = instrOwnerDot,
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
                            val instrOwnerDot = instrOwner.replace('/', '.')
                            if (matchesOwner(instrOwnerDot, owner) && matchesMethod(instrName, method)) {
                                usages.add(
                                    UsageSite(
                                        callerClass = callerClass,
                                        callerMethod = callerMethod,
                                        sourceFile = sourceFile,
                                        targetOwner = instrOwnerDot,
                                        targetName = instrName,
                                        targetDescriptor = instrDescriptor,
                                        kind = UsageKind.FIELD_ACCESS,
                                    )
                                )
                            }
                        }

                        override fun visitTypeInsn(opcode: Int, instrType: String) {
                            val instrTypeDot = instrType.replace('/', '.')
                            if (type != null && matchesType(instrTypeDot, type)) {
                                usages.add(
                                    UsageSite(
                                        callerClass = callerClass,
                                        callerMethod = callerMethod,
                                        sourceFile = sourceFile,
                                        targetOwner = instrTypeDot,
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

    private fun matchesOwner(actual: String, filter: String?): Boolean {
        if (filter == null) return false
        return actual.equals(filter, ignoreCase = true)
    }

    private fun matchesMethod(actual: String, filter: String?): Boolean {
        if (filter == null) return true
        return actual == filter
    }

    private fun matchesType(actual: String, filter: String): Boolean =
        actual.equals(filter, ignoreCase = true)

    private fun extractTypesFromDescriptor(descriptor: String): List<String> {
        val type = runCatching { Type.getType(descriptor) }.getOrNull() ?: return emptyList()
        val types = mutableListOf<String>()
        when (type.sort) {
            Type.METHOD -> {
                collectType(type.returnType, types)
                type.argumentTypes.forEach { collectType(it, types) }
            }
            else -> collectType(type, types)
        }
        return types
    }

    private fun collectType(type: Type, into: MutableList<String>) {
        when (type.sort) {
            Type.OBJECT -> into.add(type.internalName.replace('/', '.'))
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
