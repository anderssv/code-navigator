package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.callgraph.UsageKind
import no.f12.codenavigator.navigation.callgraph.UsageScanner
import no.f12.codenavigator.navigation.callgraph.UsageSite
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class UsageScannerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    // --- Method call usages ---

    @Test
    fun `finds method call usage by owner and method name`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/Target", "Target.kt")

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertEquals(ClassName("com.example.Caller"), usages[0].callerClass)
        assertEquals("doWork", usages[0].callerMethod)
        assertEquals(ClassName("com.example.Target"), usages[0].targetOwner)
        assertEquals("process", usages[0].targetName)
    }

    @Test
    fun `returns empty list when no usages match`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.NonExistent",
            method = "missing",
        ).data

        assertTrue(usages.isEmpty())
    }

    @Test
    fun `finds multiple call sites for the same target method`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/CallerA", "CallerA.kt",
            "fromA", listOf(Call("com/example/Target", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/CallerB", "CallerB.kt",
            "fromB", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(2, usages.size)
        val callerMethods = usages.map { it.callerMethod }.toSet()
        assertTrue("fromA" in callerMethods)
        assertTrue("fromB" in callerMethods)
    }

    @Test
    fun `finds all methods called on an owner when no method filter given`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "validate", "(Ljava/lang/String;)Z"),
            ),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
        ).data

        assertEquals(2, usages.size)
        val targetNames = usages.map { it.targetName }.toSet()
        assertTrue("process" in targetNames)
        assertTrue("validate" in targetNames)
    }

    // --- Field access usages ---

    @Test
    fun `finds field read (GETFIELD) usage`() {
        TestClassWriter.writeClassWithFieldAccessAndCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork",
            FieldAccess("com/example/Target", "name", "Ljava/lang/String;", Opcodes.GETFIELD),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "name",
        ).data

        assertEquals(1, usages.size)
        assertEquals(ClassName("com.example.Caller"), usages[0].callerClass)
        assertEquals("doWork", usages[0].callerMethod)
        assertEquals(ClassName("com.example.Target"), usages[0].targetOwner)
        assertEquals("name", usages[0].targetName)
        assertEquals(UsageKind.FIELD_ACCESS, usages[0].kind)
    }

    @Test
    fun `finds static field access (GETSTATIC) usage`() {
        TestClassWriter.writeClassWithFieldAccessAndCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork",
            FieldAccess("com/example/Constants", "MAX_SIZE", "I", Opcodes.GETSTATIC),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Constants",
            method = "MAX_SIZE",
        ).data

        assertEquals(1, usages.size)
        assertEquals(UsageKind.FIELD_ACCESS, usages[0].kind)
        assertEquals("MAX_SIZE", usages[0].targetName)
    }

    // --- Type references ---

    @Test
    fun `finds type used in NEW instruction`() {
        TestClassWriter.writeClassWithTypeInsn(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", Opcodes.NEW, "com/example/Target",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals(ClassName("com.example.Target"), usages[0].targetOwner)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    @Test
    fun `finds type used in CHECKCAST instruction`() {
        TestClassWriter.writeClassWithTypeInsn(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", Opcodes.CHECKCAST, "com/example/Target",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    @Test
    fun `finds type used as method parameter in descriptor`() {
        TestClassWriter.writeClassWithMethodDescriptor(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", "(Lcom/example/Target;)V",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals(ClassName("com.example.Caller"), usages[0].callerClass)
        assertEquals("doWork", usages[0].callerMethod)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    @Test
    fun `finds type used as method return type in descriptor`() {
        TestClassWriter.writeClassWithMethodDescriptor(
            classesDir, "com/example/Caller", "Caller.kt",
            "getTarget", "()Lcom/example/Target;",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals("getTarget", usages[0].callerMethod)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    @Test
    fun `finds type used as field declaration type`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Caller", "Caller.kt") {
            visitField(Opcodes.ACC_PRIVATE, "target", "Lcom/example/Target;", null, null)
        }

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals(ClassName("com.example.Caller"), usages[0].callerClass)
        assertEquals("target", usages[0].targetName)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    // --- Type parameter also matches method call/field owners ---

    @Test
    fun `type parameter with method filter narrows to specific method`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "validate", "(Ljava/lang/String;)Z"),
            ),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
            method = "process",
        ).data

        val methodCalls = usages.filter { it.kind == UsageKind.METHOD_CALL }
        assertEquals(1, methodCalls.size)
        assertEquals("process", methodCalls[0].targetName)
    }

    @Test
    fun `type parameter finds method calls and type references for same type`() {
        TestClassWriter.writeClassWithStaticCall(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", Call("com/example/Target", "process", "()V"),
        )
        TestClassWriter.writeClassWithTypeInsn(
            classesDir, "com/example/Creator", "Creator.kt",
            "create", Opcodes.NEW, "com/example/Target",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        val kinds = usages.map { it.kind }.toSet()
        assertTrue(UsageKind.METHOD_CALL in kinds, "Expected METHOD_CALL in results")
        assertTrue(UsageKind.TYPE_REFERENCE in kinds, "Expected TYPE_REFERENCE in results")
    }

    @Test
    fun `type parameter finds field access where owner matches the type`() {
        TestClassWriter.writeClassWithFieldAccessAndCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork",
            FieldAccess("com/example/Constants", "MAX_SIZE", "I", Opcodes.GETSTATIC),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Constants",
        ).data

        val fieldAccesses = usages.filter { it.kind == UsageKind.FIELD_ACCESS }
        assertEquals(1, fieldAccesses.size)
        assertEquals(ClassName("com.example.Constants"), fieldAccesses[0].targetOwner)
        assertEquals("MAX_SIZE", fieldAccesses[0].targetName)
    }

    @Test
    fun `type parameter finds method calls where owner matches the type`() {
        TestClassWriter.writeClassWithStaticCall(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", Call("com/example/ContextKt", "locateResourceFile", "(Ljava/lang/String;)Ljava/lang/String;"),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.ContextKt",
        ).data

        val methodCalls = usages.filter { it.kind == UsageKind.METHOD_CALL }
        assertEquals(1, methodCalls.size)
        assertEquals(ClassName("com.example.Caller"), methodCalls[0].callerClass)
        assertEquals("doWork", methodCalls[0].callerMethod)
        assertEquals(ClassName("com.example.ContextKt"), methodCalls[0].targetOwner)
        assertEquals("locateResourceFile", methodCalls[0].targetName)
    }

    // --- Filtering ---

    @Test
    fun `filters usages to project classes only`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/ProjectCaller", "ProjectCaller.kt",
            "doWork", listOf(Call("com/external/Library", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/external/OtherCaller", "OtherCaller.kt",
            "doOther", listOf(Call("com/external/Library", "process", "()V")),
        )

        val allUsages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.external.Library",
            method = "process",
        ).data

        assertEquals(2, allUsages.size)

        val projectSources = setOf(ClassName("com.example.ProjectCaller"))
        val projectOnly = allUsages.filter { it.callerClass in projectSources }
        assertEquals(1, projectOnly.size)
        assertEquals(ClassName("com.example.ProjectCaller"), projectOnly[0].callerClass)
    }

    @Test
    fun `ownerClass filter matches case-insensitively`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "COM.EXAMPLE.TARGET",
            method = "process",
        ).data

        assertEquals(1, usages.size)
    }

    @Test
    fun `method filter matches exact bytecode method name`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "processAll", "()V"),
            ),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertEquals("process", usages[0].targetName)
    }

    // --- Edge cases ---

    @Test
    fun `empty class directory produces no usages`() {
        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
        ).data

        assertTrue(usages.isEmpty())
    }

    @Test
    fun `includes descriptor in method usage results`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "(Ljava/lang/String;)I")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertEquals("(Ljava/lang/String;)I", usages[0].targetDescriptor)
        assertEquals(UsageKind.METHOD_CALL, usages[0].kind)
    }

    @Test
    fun `tracks source file for caller class`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/MyService", "MyService.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertEquals("MyService.kt", usages[0].sourceFile)
    }

    // --- Deduplication ---

    @Test
    fun `deduplicates when same method is called multiple times from same caller`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "process", "()V"),
            ),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size, "Duplicate calls from same caller should be deduplicated")
    }

    // --- Field parameter (property-aware usages) ---

    @Test
    fun `field parameter finds direct field access on owner`() {
        TestClassWriter.writeClassWithFieldAccessAndCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork",
            FieldAccess("com/example/Account", "accountNumber", "Ljava/lang/String;", Opcodes.GETFIELD),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Account",
            field = "accountNumber",
        ).data

        assertEquals(1, usages.size)
        assertEquals(UsageKind.FIELD_ACCESS, usages[0].kind)
        assertEquals("accountNumber", usages[0].targetName)
    }

    @Test
    fun `field parameter finds getter method call on owner`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Account", "getAccountNumber", "()Ljava/lang/String;")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Account",
            field = "accountNumber",
        ).data

        assertEquals(1, usages.size)
        assertEquals(UsageKind.METHOD_CALL, usages[0].kind)
        assertEquals("getAccountNumber", usages[0].targetName)
    }

    @Test
    fun `field parameter finds setter method call on owner`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Account", "setAccountNumber", "(Ljava/lang/String;)V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Account",
            field = "accountNumber",
        ).data

        assertEquals(1, usages.size)
        assertEquals(UsageKind.METHOD_CALL, usages[0].kind)
        assertEquals("setAccountNumber", usages[0].targetName)
    }

    @Test
    fun `field parameter finds is-prefixed method call on owner`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Account", "isActive", "()Z")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Account",
            field = "active",
        ).data

        assertEquals(1, usages.size)
        assertEquals(UsageKind.METHOD_CALL, usages[0].kind)
        assertEquals("isActive", usages[0].targetName)
    }

    @Test
    fun `field parameter finds both field access and getter call combined`() {
        TestClassWriter.writeClassWithFieldAccessAndCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork",
            fieldAccess = FieldAccess("com/example/Account", "accountNumber", "Ljava/lang/String;", Opcodes.GETFIELD),
            calls = listOf(Call("com/example/Account", "getAccountNumber", "()Ljava/lang/String;")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Account",
            field = "accountNumber",
        ).data

        assertEquals(2, usages.size)
        val kinds = usages.map { it.kind }.toSet()
        assertTrue(UsageKind.FIELD_ACCESS in kinds)
        assertTrue(UsageKind.METHOD_CALL in kinds)
    }

    @Test
    fun `field parameter does not match unrelated methods on same owner`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(
                Call("com/example/Account", "getAccountNumber", "()Ljava/lang/String;"),
                Call("com/example/Account", "process", "()V"),
                Call("com/example/Account", "validate", "()Z"),
            ),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Account",
            field = "accountNumber",
        ).data

        assertEquals(1, usages.size)
        assertEquals("getAccountNumber", usages[0].targetName)
    }

    // --- Simple name matching (consistent with other tasks) ---

    @Test
    fun `ownerClass simple name matches fully-qualified class name`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertEquals(ClassName("com.example.Target"), usages[0].targetOwner)
    }

    @Test
    fun `type simple name matches fully-qualified class name`() {
        TestClassWriter.writeClassWithTypeInsn(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", Opcodes.NEW, "com/example/Target",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "Target",
        ).data

        assertTrue(usages.isNotEmpty())
        assertEquals(ClassName("com.example.Target"), usages[0].targetOwner)
    }

    // --- Outside-package filter ---

    @Test
    fun `outside-package filter excludes usages where caller is inside the specified package`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/ra/InternalCaller", "InternalCaller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/services/ExternalCaller", "ExternalCaller.kt",
            "callTarget", listOf(Call("com/example/Target", "process", "()V")),
        )

        val allUsages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(2, allUsages.size)

        val filtered = UsageScanner.filterOutsidePackage(allUsages, "com.example.ra")

        assertEquals(1, filtered.size)
        assertEquals(ClassName("com.example.services.ExternalCaller"), filtered[0].callerClass)
    }

    @Test
    fun `outside-package filter keeps usages where caller is outside the specified package`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/services/CallerA", "CallerA.kt",
            "fromA", listOf(Call("com/example/Target", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/web/CallerB", "CallerB.kt",
            "fromB", listOf(Call("com/example/Target", "process", "()V")),
        )

        val allUsages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        val filtered = UsageScanner.filterOutsidePackage(allUsages, "com.example.ra")

        assertEquals(2, filtered.size)
    }

    @Test
    fun `outside-package filter with null value returns all usages`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/ra/InternalCaller", "InternalCaller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val allUsages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        val filtered = UsageScanner.filterOutsidePackage(allUsages, null)

        assertEquals(allUsages.size, filtered.size)
    }

    @Test
    fun `outside-package filter does not exclude package with same prefix`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/ra/InternalCaller", "InternalCaller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/rabbit/SimilarCaller", "SimilarCaller.kt",
            "callTarget", listOf(Call("com/example/Target", "process", "()V")),
        )

        val allUsages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(2, allUsages.size)

        val filtered = UsageScanner.filterOutsidePackage(allUsages, "com.example.ra")

        assertEquals(1, filtered.size)
        assertEquals(ClassName("com.example.rabbit.SimilarCaller"), filtered[0].callerClass)
    }

    // --- Source set tagging ---

    @Test
    fun `scanTagged assigns source set to usages from each directory`() {
        val mainDir = tempDir.resolve("main-classes").toFile()
        mainDir.mkdirs()
        val testDir = tempDir.resolve("test-classes").toFile()
        testDir.mkdirs()

        TestClassWriter.writeClassWithCalls(
            mainDir, "com/example/Service", "Service.kt",
            "handle", listOf(Call("com/example/Repository", "save", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            testDir, "com/example/ServiceTest", "ServiceTest.kt",
            "testHandle", listOf(Call("com/example/Repository", "save", "()V")),
        )

        val taggedDirs = listOf(mainDir to SourceSet.MAIN, testDir to SourceSet.TEST)
        val usages = UsageScanner.scanTagged(
            taggedDirs,
            ownerClass = "com.example.Repository",
            method = "save",
        ).data

        assertEquals(2, usages.size)
        val mainUsage = usages.first { it.callerClass == ClassName("com.example.Service") }
        val testUsage = usages.first { it.callerClass == ClassName("com.example.ServiceTest") }
        assertEquals(SourceSet.MAIN, mainUsage.sourceSet)
        assertEquals(SourceSet.TEST, testUsage.sourceSet)
    }

    @Test
    fun `scan without tags produces null source set`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertNull(usages[0].sourceSet)
    }
}
