package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.classinfo.ClassInfo
import no.f12.codenavigator.navigation.changedsince.SourceFileResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceFileResolverTest {

    @Test
    fun `resolves kotlin source path to matching class name`() {
        val classInfos = listOf(
            classInfo("com.example.Service", "com/example/Service.kt"),
        )

        val result = SourceFileResolver.resolve(
            gitPaths = listOf("src/main/kotlin/com/example/Service.kt"),
            classInfos = classInfos,
        )

        assertEquals(1, result.resolved.size)
        assertEquals(ClassName("com.example.Service"), result.resolved.keys.first())
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun `resolves java source path to matching class name`() {
        val classInfos = listOf(
            classInfo("com.example.Service", "com/example/Service.java"),
        )

        val result = SourceFileResolver.resolve(
            gitPaths = listOf("src/main/java/com/example/Service.java"),
            classInfos = classInfos,
        )

        assertEquals(1, result.resolved.size)
        assertEquals(ClassName("com.example.Service"), result.resolved.keys.first())
    }

    @Test
    fun `multiple classes in same source file all resolve`() {
        val classInfos = listOf(
            classInfo("com.example.Foo", "com/example/Foo.kt"),
            classInfo("com.example.Bar", "com/example/Foo.kt"),
        )

        val result = SourceFileResolver.resolve(
            gitPaths = listOf("src/main/kotlin/com/example/Foo.kt"),
            classInfos = classInfos,
        )

        assertEquals(2, result.resolved.size)
        assertTrue(result.resolved.containsKey(ClassName("com.example.Foo")))
        assertTrue(result.resolved.containsKey(ClassName("com.example.Bar")))
    }

    @Test
    fun `source path with no matching classes goes to unresolved`() {
        val classInfos = listOf(
            classInfo("com.example.Service", "com/example/Service.kt"),
        )

        val result = SourceFileResolver.resolve(
            gitPaths = listOf("src/main/kotlin/com/example/Unknown.kt"),
            classInfos = classInfos,
        )

        assertTrue(result.resolved.isEmpty())
        assertEquals(listOf("src/main/kotlin/com/example/Unknown.kt"), result.unresolved)
    }

    @Test
    fun `non-source files go to unresolved`() {
        val classInfos = listOf(
            classInfo("com.example.Service", "com/example/Service.kt"),
        )

        val result = SourceFileResolver.resolve(
            gitPaths = listOf("build.gradle.kts", "README.md"),
            classInfos = classInfos,
        )

        assertTrue(result.resolved.isEmpty())
        assertEquals(listOf("build.gradle.kts", "README.md"), result.unresolved)
    }

    @Test
    fun `handles source root prefix src-main-groovy`() {
        val classInfos = listOf(
            classInfo("com.example.Script", "com/example/Script.groovy"),
        )

        val result = SourceFileResolver.resolve(
            gitPaths = listOf("src/main/groovy/com/example/Script.groovy"),
            classInfos = classInfos,
        )

        assertEquals(1, result.resolved.size)
    }

    @Test
    fun `suffix matching works when git path has unexpected source root`() {
        val classInfos = listOf(
            classInfo("com.example.Service", "com/example/Service.kt"),
        )

        val result = SourceFileResolver.resolve(
            gitPaths = listOf("app/src/main/kotlin/com/example/Service.kt"),
            classInfos = classInfos,
        )

        assertEquals(1, result.resolved.size)
        assertEquals(ClassName("com.example.Service"), result.resolved.keys.first())
    }

    private fun classInfo(className: String, reconstructedSourcePath: String) =
        ClassInfo(
            className = ClassName(className),
            sourceFileName = reconstructedSourcePath.substringAfterLast('/'),
            reconstructedSourcePath = reconstructedSourcePath,
            isUserDefinedClass = true,
        )
}
