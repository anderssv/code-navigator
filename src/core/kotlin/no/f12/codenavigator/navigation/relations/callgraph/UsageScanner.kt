package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.bytecode.ScanResult
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.navigation.types.TypeMatcher
import no.f12.codenavigator.navigation.bytecode.UnsupportedBytecodeVersionException
import no.f12.codenavigator.navigation.bytecode.createClassReader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
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
    val sourceSet: SourceSet?,
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
    ): ScanResult<List<UsageSite>> =
        scanTagged(
            taggedDirectories = classDirectories.map { it to null },
            ownerClass = ownerClass,
            method = method,
            field = field,
            type = type,
        )

    fun scanTagged(
        taggedDirectories: List<Pair<File, SourceSet?>>,
        ownerClass: String? = null,
        method: String? = null,
        field: String? = null,
        type: String? = null,
    ): ScanResult<List<UsageSite>> {
        val ownerMatcher = ownerClass?.let { TypeMatcher.RegexMatcher(Regex(it, RegexOption.IGNORE_CASE)) }
        val typeMatcher = type?.let { TypeMatcher.RegexMatcher(Regex(it, RegexOption.IGNORE_CASE)) }
        return scanTagged(taggedDirectories, ownerMatcher = ownerMatcher, method = method, field = field, typeMatcher = typeMatcher)
    }

    fun scanTagged(
        taggedDirectories: List<Pair<File, SourceSet?>>,
        ownerMatcher: TypeMatcher? = null,
        method: String? = null,
        field: String? = null,
        typeMatcher: TypeMatcher? = null,
    ): ScanResult<List<UsageSite>> {
        val usages = mutableSetOf<UsageSite>()
        val skipped = mutableListOf<UnsupportedBytecodeVersionException>()

        taggedDirectories
            .filter { it.first.exists() }
            .forEach { (dir, sourceSet) ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { classFile ->
                        try {
                            extractUsages(classFile, ownerMatcher, method, field, typeMatcher, usages, sourceSet)
                        } catch (e: UnsupportedBytecodeVersionException) {
                            skipped.add(e)
                        }
                    }
            }

        return ScanResult(usages.toList(), skipped)
    }

    private fun extractUsages(
        classFile: File,
        ownerMatcher: TypeMatcher?,
        method: String?,
        field: String?,
        typeMatcher: TypeMatcher?,
        usages: MutableCollection<UsageSite>,
        sourceSet: SourceSet?,
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
                    if (typeMatcher != null) {
                        val referencedTypes = extractTypesFromDescriptor(descriptor)
                        val matchedType = referencedTypes.firstOrNull { typeMatcher.matches(it) }
                        if (matchedType != null) {
                            usages.add(
                                UsageSite(
                                    callerClass = callerClass,
                                    callerMethod = "<field>",
                                    sourceFile = sourceFile,
                                    targetOwner = matchedType,
                                    targetName = name,
                                    targetDescriptor = descriptor,
                                    kind = UsageKind.TYPE_REFERENCE,
                                    sourceSet = sourceSet,
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

                    if (typeMatcher != null) {
                        val referencedTypes = extractTypesFromDescriptor(descriptor)
                        val matchedType = referencedTypes.firstOrNull { typeMatcher.matches(it) }
                        if (matchedType != null) {
                            usages.add(
                                UsageSite(
                                    callerClass = callerClass,
                                    callerMethod = callerMethod,
                                    sourceFile = sourceFile,
                                    targetOwner = matchedType,
                                    targetName = callerMethod,
                                    targetDescriptor = descriptor,
                                    kind = UsageKind.TYPE_REFERENCE,
                                    sourceSet = sourceSet,
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
                            val ownerMatched = field == null && matchesOwner(instrOwnerClass, ownerMatcher) && matchesMethod(instrName, method)
                            val fieldMatched = field != null && matchesOwner(instrOwnerClass, ownerMatcher) && matchesFieldAccessor(instrName, field)
                            val typeMatched = typeMatcher != null && typeMatcher.matches(instrOwnerClass) && matchesMethod(instrName, method)
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
                                        sourceSet = sourceSet,
                                    )
                                )
                            }
                        }

                        override fun visitFieldInsn(
                            opcode: Int, instrOwner: String, instrName: String,
                            instrDescriptor: String,
                        ) {
                            val instrOwnerClass = ClassName.fromInternal(instrOwner)
                            val ownerMatched = field == null && matchesOwner(instrOwnerClass, ownerMatcher) && matchesMethod(instrName, method)
                            val fieldMatched = field != null && matchesOwner(instrOwnerClass, ownerMatcher) && instrName == field
                            val typeMatched = typeMatcher != null && typeMatcher.matches(instrOwnerClass) && matchesMethod(instrName, method)
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
                                        sourceSet = sourceSet,
                                    )
                                )
                            }
                        }

                        override fun visitInvokeDynamicInsn(
                            name: String,
                            descriptor: String,
                            bootstrapMethodHandle: Handle,
                            vararg bootstrapMethodArguments: Any,
                        ) {
                            for (arg in bootstrapMethodArguments) {
                                if (arg is Handle) {
                                    val instrOwnerClass = ClassName.fromInternal(arg.owner)
                                    val ownerMatched = field == null && matchesOwner(instrOwnerClass, ownerMatcher) && matchesMethod(arg.name, method)
                                    val fieldMatched = field != null && matchesOwner(instrOwnerClass, ownerMatcher) && matchesFieldAccessor(arg.name, field)
                                    val typeMatched = typeMatcher != null && typeMatcher.matches(instrOwnerClass) && matchesMethod(arg.name, method)
                                    if (ownerMatched || fieldMatched || typeMatched) {
                                        usages.add(
                                            UsageSite(
                                                callerClass = callerClass,
                                                callerMethod = callerMethod,
                                                sourceFile = sourceFile,
                                                targetOwner = instrOwnerClass,
                                                targetName = arg.name,
                                                targetDescriptor = arg.desc,
                                                kind = UsageKind.METHOD_CALL,
                                                sourceSet = sourceSet,
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        override fun visitTypeInsn(opcode: Int, instrType: String) {
                            val instrTypeClass = ClassName.fromInternal(instrType)
                            if (typeMatcher != null && typeMatcher.matches(instrTypeClass)) {
                                usages.add(
                                    UsageSite(
                                        callerClass = callerClass,
                                        callerMethod = callerMethod,
                                        sourceFile = sourceFile,
                                        targetOwner = instrTypeClass,
                                        targetName = typeInsnName(opcode),
                                        targetDescriptor = "",
                                        kind = UsageKind.TYPE_REFERENCE,
                                        sourceSet = sourceSet,
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

    private fun matchesOwner(actual: ClassName, matcher: TypeMatcher?): Boolean {
        if (matcher == null) return false
        return matcher.matches(actual)
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
