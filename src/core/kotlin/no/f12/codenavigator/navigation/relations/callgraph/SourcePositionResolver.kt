package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.registry.TaskRegistry
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

/**
 * Turns a source position (`file` + `line`, as in a diff or a stack trace) into what find-usages searches for, from
 * the line numbers the compiler wrote into the class files. No fully qualified name needed.
 */
object SourcePositionResolver {

    sealed interface Target {
        data class Method(val owner: String, val name: String) : Target
        data class Type(val owner: String) : Target
    }

    private data class MethodLines(val owner: String, val name: String, val first: Int, val last: Int)

    /** A function's header sits just above its first body line; this is how far above the header may be. */
    private const val HEADER_LINES = 3

    fun resolve(classDirectories: List<File>, file: String, line: Int): Target {
        val normalized = file.replace('\\', '/')
        val fileName = normalized.substringAfterLast('/')
        val methods = mutableListOf<MethodLines>()
        val classes = mutableListOf<String>()
        classDirectories.filter { it.exists() }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { classFile ->
                scan(classFile, normalized, fileName, methods, classes)
            }
        }
        require(classes.isNotEmpty()) {
            "No compiled classes come from $file. Check the path (relative paths are matched by package directory and file name) and that it is compiled."
        }
        val constructorHeader = methods.firstOrNull { it.name == "<init>" && it.first == line }
        if (constructorHeader != null) return Target.Type(constructorHeader.owner)

        val containing = methods.filter { it.name != "<init>" && line in it.first..it.last }.minByOrNull { it.last - it.first }
        if (containing != null) return Target.Method(containing.owner, containing.name)

        val below = methods.filter { it.name != "<init>" && it.first > line && it.first - line <= HEADER_LINES }.minByOrNull { it.first }
        if (below != null && methods.none { it.name == "<init>" && line in it.first..it.last }) return Target.Method(below.owner, below.name)

        val owner = classes.firstOrNull { it.substringAfterLast('.') == fileName.removeSuffix(".kt").removeSuffix(".java") } ?: classes.first()
        return Target.Type(owner)
    }

    /** Replaces `file` + `line` with the find-usages properties they point at; other properties pass through. */
    fun toUsageProperties(classDirectories: List<File>, properties: Map<String, String?>): Map<String, String?> {
        val file = properties[TaskRegistry.POSITION_FILE.name] ?: return properties
        val line = properties[TaskRegistry.POSITION_LINE.name]?.toIntOrNull()
            ?: throw IllegalArgumentException("'${TaskRegistry.POSITION_FILE.name}' needs '${TaskRegistry.POSITION_LINE.name}' (a line number) too")
        val rest = properties - TaskRegistry.POSITION_FILE.name - TaskRegistry.POSITION_LINE.name
        return when (val target = resolve(classDirectories, file, line)) {
            is Target.Method -> rest + mapOf(TaskRegistry.OWNER_CLASS.name to target.owner, TaskRegistry.METHOD.name to target.name)
            is Target.Type -> rest + mapOf(TaskRegistry.TYPE.name to target.owner)
        }
    }

    private fun scan(classFile: File, path: String, fileName: String, methods: MutableList<MethodLines>, classes: MutableList<String>) {
        val reader = ClassReader(classFile.readBytes())
        val owner = reader.className.replace('/', '.')
        val packageDir = reader.className.substringBeforeLast('/', "")
        var matches = false
        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitSource(source: String?, debug: String?) {
                matches = source == fileName && (!path.contains('/') || path.endsWith(if (packageDir.isEmpty()) fileName else "$packageDir/$fileName"))
                if (matches && !owner.contains('$')) classes += owner
            }

            override fun visitMethod(access: Int, name: String, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (!matches || access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE) != 0) return null
                if (name == "<clinit>" || name.contains('$')) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    var first = Int.MAX_VALUE
                    var last = Int.MIN_VALUE
                    override fun visitLineNumber(line: Int, start: Label?) {
                        first = minOf(first, line); last = maxOf(last, line)
                    }
                    override fun visitEnd() {
                        if (first != Int.MAX_VALUE) methods += MethodLines(owner, name, first, last)
                    }
                }
            }
        }, ClassReader.SKIP_FRAMES)
    }
}
