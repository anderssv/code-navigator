package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.dsm.DsmDependencyExtractor
import no.f12.codenavigator.navigation.dsm.PackageDependency
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DsmDependencyExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `empty directory produces no dependencies`() {
        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("")).data

        assertTrue(deps.isEmpty())
    }

    @Test
    fun `detects method call dependency between packages`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Expected dependency from Controller to Service")
        assertEquals(PackageName("com.example.api"), dep.sourcePackage)
        assertEquals(PackageName("com.example.service"), dep.targetPackage)
    }

    @Test
    fun `excludes same-package dependencies`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/ControllerA", "ControllerA.kt",
            "handle", listOf(Call("com/example/api/ControllerB", "other", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/api/ControllerB", "ControllerB.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        assertTrue(deps.isEmpty(), "Same-package deps should be excluded")
    }

    @Test
    fun `detects field type dependency`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/api/Controller", "Controller.kt") {
            visitField(Opcodes.ACC_PRIVATE, "service", "Lcom/example/service/Service;", null, null)
        }
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Expected dependency from field type")
    }

    @Test
    fun `detects superclass dependency`() {
        TestClassWriter.writeClassFile(
            classesDir, "com/example/impl/ConcreteService", "ConcreteService.kt",
            superName = "com/example/base/AbstractService",
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/base/AbstractService", "AbstractService.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.impl.ConcreteService") && it.targetClass == ClassName("com.example.base.AbstractService") }
        assertTrue(dep != null, "Expected dependency from superclass")
    }

    @Test
    fun `detects interface implementation dependency`() {
        TestClassWriter.writeClassFile(
            classesDir, "com/example/impl/UserRepo", "UserRepo.kt",
            interfaces = arrayOf("com/example/domain/Repository"),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/domain/Repository", "Repository.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.impl.UserRepo") && it.targetClass == ClassName("com.example.domain.Repository") }
        assertTrue(dep != null, "Expected dependency from interface implementation")
    }

    @Test
    fun `filters by root prefix`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/other/lib/Helper", "help", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/other/lib/Helper", "Helper.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        assertTrue(deps.isEmpty(), "Dependencies outside root prefix should be excluded")
    }

    @Test
    fun `empty root prefix includes all packages`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/other/lib/Helper", "help", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/other/lib/Helper", "Helper.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.other.lib.Helper") }
        assertTrue(dep != null, "Empty root prefix should include all packages")
    }

    @Test
    fun `strips inner class names to base class`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service\$Companion", "getInstance", "()Lcom/example/service/Service;")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Inner class reference should resolve to base class")
    }

    @Test
    fun `produces unique dependencies per class pair`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(
                Call("com/example/service/Service", "process", "()V"),
                Call("com/example/service/Service", "validate", "()V"),
            ),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val matching = deps.filter { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertEquals(1, matching.size, "Should deduplicate to one dependency per class pair")
    }

    @Test
    fun `detects dependency from field access instruction in method body`() {
        TestClassWriter.writeClassWithFieldAccessAndCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle",
            FieldAccess("com/example/service/Service", "instance", "Lcom/example/service/Service;", Opcodes.GETSTATIC),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Expected dependency from field access in method body")
    }

    @Test
    fun `detects dependency from type instruction (NEW)`() {
        TestClassWriter.writeClassWithTypeInsn(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "create", Opcodes.NEW, "com/example/model/Entity",
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/model/Entity", "Entity.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.model.Entity") }
        assertTrue(dep != null, "Expected dependency from NEW type instruction")
    }

    @Test
    fun `detects dependency from class literal (LDC Type)`() {
        TestClassWriter.writeClassWithLdcType(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "getType", "com/example/model/Entity",
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/model/Entity", "Entity.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.model.Entity") }
        assertTrue(dep != null, "Expected dependency from class literal LDC")
    }

    @Test
    fun `detects dependency through method parameter descriptor`() {
        TestClassWriter.writeClassWithMethodDescriptor(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", "(Lcom/example/model/Request;)Lcom/example/model/Response;",
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/model/Request", "Request.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/model/Response", "Response.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val request = deps.find { it.targetClass == ClassName("com.example.model.Request") }
        val response = deps.find { it.targetClass == ClassName("com.example.model.Response") }
        assertTrue(request != null, "Expected dependency from method parameter type")
        assertTrue(response != null, "Expected dependency from method return type")
    }

    @Test
    fun `detects dependency through exception declaration`() {
        TestClassWriter.writeClassWithException(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", "com/example/errors/AppException",
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/errors/AppException", "AppException.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.targetClass == ClassName("com.example.errors.AppException") }
        assertTrue(dep != null, "Expected dependency from throws declaration")
    }

    @Test
    fun `detects dependency through array field type`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/api/Controller", "Controller.kt") {
            visitField(Opcodes.ACC_PRIVATE, "services", "[Lcom/example/service/Service;", null, null)
        }
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Expected dependency from array element type")
    }

    @Test
    fun `detects dependency from method call with object return type in descriptor`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "getModel", "()Lcom/example/model/Entity;")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/model/Entity", "Entity.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val entityDep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.model.Entity") }
        assertTrue(entityDep != null, "Expected dependency from call descriptor return type")
    }

    @Test
    fun `extracts dependencies from real compiled Kotlin classes`() {
        val classesDir = File("test-project/build/classes/kotlin/main")
        if (!classesDir.exists()) {
            buildTestProject()
        }

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        assertTrue(deps.isNotEmpty(), "Expected inter-package dependencies from test-project, but got none")
        val packages = deps.flatMap { listOf(it.sourcePackage, it.targetPackage) }.toSet()
        assertTrue(packages.contains(PackageName("com.example.services")), "Expected com.example.services in dependencies")
        assertTrue(packages.contains(PackageName("com.example.domain")), "Expected com.example.domain in dependencies")
    }

    @Test
    fun `project class filtering excludes external dependencies by default`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/other/lib/Helper", "help", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/api/HelperUser", "HelperUser.kt")

        val projectClasses = setOf(
            ClassName("com.example.api.Controller"),
            ClassName("com.example.api.HelperUser"),
        )

        val deps = DsmDependencyExtractor.extract(classDirectories = listOf(classesDir), projectClasses = projectClasses).data

        assertTrue(deps.isEmpty(), "Dependencies on non-project classes should be excluded")
    }

    @Test
    fun `project class filtering includes project-to-project dependencies`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val projectClasses = setOf(
            ClassName("com.example.api.Controller"),
            ClassName("com.example.service.Service"),
        )

        val deps = DsmDependencyExtractor.extract(classDirectories = listOf(classesDir), projectClasses = projectClasses).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Expected project-to-project dependency")
    }

    @Test
    fun `include-external shows dependencies on non-project classes`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/other/lib/Helper", "help", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/other/lib/Helper", "Helper.kt")

        val projectClasses = setOf(ClassName("com.example.api.Controller"))

        val deps = DsmDependencyExtractor.extract(
            classDirectories = listOf(classesDir),
            projectClasses = projectClasses,
            includeExternal = true,
        ).data

        val dep = deps.find { it.targetClass == ClassName("com.other.lib.Helper") }
        assertTrue(dep != null, "External dependencies should be included when include-external is true")
    }

    @Test
    fun `package-filter narrows to matching packages only`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/other/Other", "Other.kt",
            "run", listOf(Call("com/example/service/Service", "process", "()V")),
        )

        val projectClasses = setOf(
            ClassName("com.example.api.Controller"),
            ClassName("com.example.service.Service"),
            ClassName("com.example.other.Other"),
        )

        val deps = DsmDependencyExtractor.extract(
            classDirectories = listOf(classesDir),
            projectClasses = projectClasses,
            packageFilter = PackageName("com.example.api"),
        ).data

        assertTrue(deps.all { it.sourcePackage.startsWith(PackageName("com.example.api")) }, "Only sources matching package-filter should be included")
    }

    @Test
    fun `source-only filter includes targets outside filter`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val projectClasses = setOf(
            ClassName("com.example.api.Controller"),
            ClassName("com.example.service.Service"),
        )

        val deps = DsmDependencyExtractor.extract(
            classDirectories = listOf(classesDir),
            projectClasses = projectClasses,
            packageFilter = PackageName("com.example.api"),
            filterTargets = false,
        ).data

        val dep = deps.find {
            it.sourceClass == ClassName("com.example.api.Controller") &&
                it.targetClass == ClassName("com.example.service.Service")
        }
        assertTrue(dep != null, "Source-only filter should include targets outside the filter (api -> service)")
    }

    @Test
    fun `source-only filter still excludes sources outside filter`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/other/Other", "Other.kt",
            "run", listOf(Call("com/example/api/Controller", "handle", "()V")),
        )

        val projectClasses = setOf(
            ClassName("com.example.api.Controller"),
            ClassName("com.example.service.Service"),
            ClassName("com.example.other.Other"),
        )

        val deps = DsmDependencyExtractor.extract(
            classDirectories = listOf(classesDir),
            projectClasses = projectClasses,
            packageFilter = PackageName("com.example.api"),
            filterTargets = false,
        ).data

        assertTrue(
            deps.all { it.sourcePackage.startsWith(PackageName("com.example.api")) },
            "Source-only filter should still exclude sources outside the filter",
        )
    }

    @Test
    fun `default filterTargets is true for backward compatibility`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val projectClasses = setOf(
            ClassName("com.example.api.Controller"),
            ClassName("com.example.service.Service"),
        )

        val deps = DsmDependencyExtractor.extract(
            classDirectories = listOf(classesDir),
            projectClasses = projectClasses,
            packageFilter = PackageName("com.example.api"),
        ).data

        assertTrue(deps.isEmpty(), "Default filterTargets=true should filter out targets outside the filter (existing DSM behavior)")
    }

    private fun buildTestProject() {
        val testProjectDir = File("test-project")
        val gradlew = File(testProjectDir.parentFile, "gradlew").absolutePath
        val process = ProcessBuilder(gradlew, "classes")
            .directory(testProjectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "Failed to build test-project (exit $exitCode): $output" }
    }
}
