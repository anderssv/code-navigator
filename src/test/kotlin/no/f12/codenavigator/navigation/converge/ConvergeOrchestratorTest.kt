package no.f12.codenavigator.navigation.converge

import no.f12.codenavigator.analysis.FileChange
import no.f12.codenavigator.analysis.GitCommit
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.*
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvergeOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File
    private lateinit var taggedDirs: List<Pair<File, SourceSet>>
    private lateinit var reportFile: File
    private lateinit var cacheFile: File
    private lateinit var projectDir: File

    private fun config(
        mode: ConvergeMode = ConvergeMode.INTERSECT,
        minSharedRevs: Int = 5,
        minCoupling: Int = 30,
        top: Int = 50,
        exclude: Regex? = null,
    ) = ConvergeConfig(
        mode = mode,
        packageFilter = null,
        exclude = exclude,
        after = LocalDate.of(2000, 1, 1),
        minSharedRevs = minSharedRevs,
        minCoupling = minCoupling,
        maxChangesetSize = 30,
        followRenames = true,
        top = top,
        scope = Scope.ALL,
        format = OutputFormat.TEXT,
    )

    private fun coupledCommits(pathA: String, pathB: String, count: Int = 5): List<GitCommit> =
        (1..count).map { i ->
            GitCommit(
                "commit-$i",
                LocalDate.of(2024, 1, 1).plusDays(i.toLong()),
                "Author",
                listOf(FileChange(1, 0, pathA), FileChange(1, 0, pathB)),
            )
        }

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        taggedDirs = listOf(classesDir to SourceSet.MAIN)
        reportFile = tempDir.resolve("skipped.txt").toFile()
        cacheFile = tempDir.resolve("call-graph.cache").toFile()
        projectDir = tempDir.resolve("project").toFile().apply { mkdirs() }
    }

    @Test
    fun `structural and coupling agreement on the same pair yields ACT_NOW`() {
        // Bidirectional dependency = both a package cycle and a ring PEER violation.
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/service/Service", "Service.kt",
            "process", listOf(Call("com/example/api/Controller", "handle", "()V")),
        )
        val commits = coupledCommits(
            "src/main/kotlin/com/example/api/Controller.kt",
            "src/main/kotlin/com/example/service/Service.kt",
        )

        val output = ConvergeOrchestrator.run(config(), taggedDirs, commits, projectDir, cacheFile, reportFile)

        val edges = (output as ConvergeOutput.Intersect).output.edges
        val edge = edges.single { setOf(it.source, it.target) == setOf(PackageName("com.example.api"), PackageName("com.example.service")) }
        assertEquals(ConvergeVerdict.ACT_NOW, edge.verdict)
        assertTrue(edge.hasCycle)
        assertTrue(edge.couplingDegree != null && edge.couplingDegree!! >= 30)
    }

    @Test
    fun `structural signal with no coupling yields LATENT`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/service/Service", "Service.kt",
            "process", listOf(Call("com/example/api/Controller", "handle", "()V")),
        )

        val output = ConvergeOrchestrator.run(config(), taggedDirs, emptyList(), projectDir, cacheFile, reportFile)

        val edges = (output as ConvergeOutput.Intersect).output.edges
        val edge = edges.single { setOf(it.source, it.target) == setOf(PackageName("com.example.api"), PackageName("com.example.service")) }
        assertEquals(ConvergeVerdict.LATENT, edge.verdict)
        assertEquals(null, edge.couplingDegree)
    }

    @Test
    fun `coupling with no structural signal yields MISSING_ABSTRACTION`() {
        // No calls between these two classes at all — clean dependency graph.
        TestClassWriter.writeClassFile(classesDir, "com/example/web/Page", "Page.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/reporting/Report", "Report.kt")
        val commits = coupledCommits(
            "src/main/kotlin/com/example/web/Page.kt",
            "src/main/kotlin/com/example/reporting/Report.kt",
        )

        val output = ConvergeOrchestrator.run(config(), taggedDirs, commits, projectDir, cacheFile, reportFile)

        val edges = (output as ConvergeOutput.Intersect).output.edges
        val edge = edges.single { setOf(it.source, it.target) == setOf(PackageName("com.example.web"), PackageName("com.example.reporting")) }
        assertEquals(ConvergeVerdict.MISSING_ABSTRACTION, edge.verdict)
        assertEquals(false, edge.hasCycle)
        assertEquals(false, edge.hasRingViolation)
    }

    @Test
    fun `coupling pairs that cannot be resolved to a project class are counted as unresolved`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/web/Page", "Page.kt")
        val commits = coupledCommits(
            "src/main/kotlin/com/example/web/Page.kt",
            "docs/README.md",
        )

        val output = ConvergeOrchestrator.run(config(), taggedDirs, commits, projectDir, cacheFile, reportFile)

        assertEquals(1, (output as ConvergeOutput.Intersect).output.unresolvedCouplingPairs)
    }

    @Test
    fun `risk mode ranks by change frequency x complexity x coupling`() {
        // Foo calls Bar 3 times (fanOut=3), Bar has no outgoing calls (fanOut=0).
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/domain/Foo", "Foo.kt",
            "run",
            listOf(
                Call("com/example/domain/Bar", "a", "()V"),
                Call("com/example/domain/Bar", "b", "()V"),
                Call("com/example/domain/Bar", "c", "()V"),
            ),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/domain/Bar", "Bar.kt")

        val fooPath = "src/main/kotlin/com/example/domain/Foo.kt"
        File(projectDir, fooPath).apply { parentFile.mkdirs(); writeText("class Foo") }
        val commits = (1..6).map { i ->
            GitCommit("c$i", LocalDate.of(2024, 1, 1).plusDays(i.toLong()), "Author", listOf(FileChange(1, 0, fooPath)))
        }

        val output = ConvergeOrchestrator.run(config(mode = ConvergeMode.RISK), taggedDirs, commits, projectDir, cacheFile, reportFile)

        val entries = (output as ConvergeOutput.Risk).output.entries
        val fooEntry = entries.single { it.className.value == "com.example.domain.Foo" }
        assertEquals(6, fooEntry.changeFrequency)
        assertEquals(3, fooEntry.complexity)
        assertEquals(null, fooEntry.couplingDegree)
        assertEquals(6L * 3 * 1, fooEntry.riskScore)
    }

    @Test
    fun `risk mode excludes classes with no change history`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/domain/Untouched", "Untouched.kt")

        val output = ConvergeOrchestrator.run(config(mode = ConvergeMode.RISK), taggedDirs, emptyList(), projectDir, cacheFile, reportFile)

        assertTrue((output as ConvergeOutput.Risk).output.entries.isEmpty())
    }

    @Test
    fun `exclude drops edges touching the excluded package from intersect mode entirely`() {
        // di depends on (and is depended on by) two otherwise-unrelated feature packages — a
        // composition-root shape that would otherwise show up as noisy structural+coupling signal.
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/di/Wiring", "Wiring.kt",
            "configure",
            listOf(
                Call("com/example/featureA/ServiceA", "start", "()V"),
                Call("com/example/featureB/ServiceB", "start", "()V"),
            ),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/featureA/ServiceA", "ServiceA.kt",
            "start", listOf(Call("com/example/di/Wiring", "register", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/featureB/ServiceB", "ServiceB.kt",
            "start", listOf(Call("com/example/di/Wiring", "register", "()V")),
        )
        val commits = coupledCommits(
            "src/main/kotlin/com/example/di/Wiring.kt",
            "src/main/kotlin/com/example/featureA/ServiceA.kt",
        )

        val output = ConvergeOrchestrator.run(
            config(exclude = Regex("\\.di(\\.|$)")), taggedDirs, commits, projectDir, cacheFile, reportFile,
        )

        val edges = (output as ConvergeOutput.Intersect).output.edges
        assertTrue(edges.none { it.source.value.contains(".di") || it.target.value.contains(".di") })
    }

    @Test
    fun `exclude drops matching classes from risk mode entirely`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/di/Wiring", "Wiring.kt")
        val wiringPath = "src/main/kotlin/com/example/di/Wiring.kt"
        File(projectDir, wiringPath).apply { parentFile.mkdirs(); writeText("class Wiring") }
        val commits = (1..6).map { i ->
            GitCommit("c$i", LocalDate.of(2024, 1, 1).plusDays(i.toLong()), "Author", listOf(FileChange(1, 0, wiringPath)))
        }

        val output = ConvergeOrchestrator.run(
            config(mode = ConvergeMode.RISK, exclude = Regex("\\.di\\.")), taggedDirs, commits, projectDir, cacheFile, reportFile,
        )

        assertTrue((output as ConvergeOutput.Risk).output.entries.none { it.className.value.contains(".di.") })
    }

    @Test
    fun `small intersect result carries no advisory`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/service/Service", "Service.kt",
            "process", listOf(Call("com/example/api/Controller", "handle", "()V")),
        )

        val output = ConvergeOrchestrator.run(config(), taggedDirs, emptyList(), projectDir, cacheFile, reportFile)

        assertEquals(null, (output as ConvergeOutput.Intersect).output.advisory)
    }

    @Test
    fun `advisory below threshold is null`() {
        assertEquals(null, ConvergeOrchestrator.advisoryFor(19, config()))
    }

    @Test
    fun `advisory for large all-scope result suggests both scope and exclude`() {
        val advisory = ConvergeOrchestrator.advisoryFor(40, config(exclude = null).copy(scope = Scope.ALL))

        assertTrue(advisory != null)
        assertTrue(advisory!!.contains("--scope=prod"))
        assertTrue(advisory.contains("--exclude-packages"))
        assertTrue(advisory.contains("cnav-config.json"))
    }

    @Test
    fun `advisory for large prod-scope result suggests only exclude`() {
        val advisory = ConvergeOrchestrator.advisoryFor(40, config().copy(scope = Scope.PROD, exclude = null))

        assertTrue(advisory != null)
        assertTrue(!advisory!!.contains("--scope=prod"))
        assertTrue(advisory.contains("--exclude-packages"))
    }

    @Test
    fun `advisory is null when both levers already pulled`() {
        val advisory = ConvergeOrchestrator.advisoryFor(40, config(exclude = Regex("testutil")).copy(scope = Scope.PROD))

        assertEquals(null, advisory)
    }
}
