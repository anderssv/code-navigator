package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.AnnotationName
import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.callgraph.MethodRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassNameTest {

    @Test
    fun `wraps a fully qualified class name`() {
        val name = ClassName("com.example.MyService")

        assertEquals("com.example.MyService", name.value)
    }

    @Test
    fun `extracts package name`() {
        val name = ClassName("com.example.service.MyService")

        assertEquals(PackageName("com.example.service"), name.packageName())
    }

    @Test
    fun `extracts empty package for default package`() {
        val name = ClassName("MyService")

        assertEquals(PackageName(""), name.packageName())
    }

    @Test
    fun `toString returns the class name`() {
        val name = ClassName("com.example.MyService")

        assertEquals("com.example.MyService", name.toString())
    }

    @Test
    fun `equality is based on value`() {
        val a = ClassName("com.example.Foo")
        val b = ClassName("com.example.Foo")

        assertEquals(a, b)
    }

    @Test
    fun `isGenerated is true for inner classes`() {
        assertTrue(ClassName("com.example.Outer\$Inner").isGenerated())
    }

    @Test
    fun `isGenerated is true for lambda classes`() {
        assertTrue(ClassName("com.example.Service\$handle\$1").isGenerated())
    }

    @Test
    fun `isGenerated is false for regular classes`() {
        assertFalse(ClassName("com.example.MyService").isGenerated())
    }

    @Test
    fun `isPackageInfo is true for package-info classes`() {
        assertTrue(ClassName("com.example.package-info").isPackageInfo())
    }

    @Test
    fun `isPackageInfo is false for regular classes`() {
        assertFalse(ClassName("com.example.MyService").isPackageInfo())
    }

    @Test
    fun `isSynthetic is true for anonymous classes`() {
        assertTrue(ClassName("com.example.Foo\$1").isSynthetic())
    }

    @Test
    fun `isSynthetic is true for lambda classes`() {
        assertTrue(ClassName("com.example.Service\$lambda\$1").isSynthetic())
    }

    @Test
    fun `isSynthetic is false for named inner classes`() {
        assertFalse(ClassName("com.example.Outer\$Inner").isSynthetic())
    }

    @Test
    fun `isSynthetic is false for regular classes`() {
        assertFalse(ClassName("com.example.MyService").isSynthetic())
    }

    // --- matches ---

    @Test
    fun `matches returns true when regex matches class name`() {
        assertTrue(ClassName("com.example.MyService").matches(Regex("Service")))
    }

    @Test
    fun `matches returns false when regex does not match`() {
        assertFalse(ClassName("com.example.MyService").matches(Regex("Controller")))
    }

    @Test
    fun `matches works with anchored regex`() {
        assertTrue(ClassName("com.example.MyService").matches(Regex("^com\\.example")))
    }

    // --- outerClass ---

    @Test
    fun `outerClass strips last dollar segment`() {
        assertEquals(ClassName("com.example.Outer"), ClassName("com.example.Outer\$Inner").outerClass())
    }

    @Test
    fun `outerClass strips lambda segment`() {
        assertEquals(ClassName("com.example.Service\$handle"), ClassName("com.example.Service\$handle\$1").outerClass())
    }

    @Test
    fun `outerClass returns same class when no dollar sign`() {
        assertEquals(ClassName("com.example.MyService"), ClassName("com.example.MyService").outerClass())
    }

    // --- startsWith ---

    @Test
    fun `startsWith with PackageName matches on package prefix`() {
        assertTrue(ClassName("com.example.MyService").startsWith(PackageName("com.example")))
    }

    @Test
    fun `startsWith with PackageName rejects non-matching prefix`() {
        assertFalse(ClassName("com.example.MyService").startsWith(PackageName("com.other")))
    }

    @Test
    fun `startsWith with empty PackageName matches everything`() {
        assertTrue(ClassName("com.example.MyService").startsWith(PackageName("")))
    }

    // --- fromInternal ---

    @Test
    fun `fromInternal converts slashes to dots`() {
        assertEquals(ClassName("com.example.MyService"), ClassName.fromInternal("com/example/MyService"))
    }

    @Test
    fun `fromInternal handles default package`() {
        assertEquals(ClassName("MyService"), ClassName.fromInternal("MyService"))
    }

    // --- simpleName ---

    @Test
    fun `simpleName returns class name without package`() {
        assertEquals("MyService", ClassName("com.example.MyService").simpleName())
    }

    @Test
    fun `simpleName strips inner class suffix`() {
        assertEquals("Outer", ClassName("com.example.Outer\$Inner").simpleName())
    }

    @Test
    fun `simpleName for default package class`() {
        assertEquals("MyService", ClassName("MyService").simpleName())
    }

    // --- topLevelClass ---

    @Test
    fun `topLevelClass strips all dollar segments from inner class`() {
        assertEquals(ClassName("com.example.Outer"), ClassName("com.example.Outer\$Inner").topLevelClass())
    }

    @Test
    fun `topLevelClass strips all dollar segments from lambda class`() {
        assertEquals(ClassName("com.example.Service"), ClassName("com.example.Service\$handle\$1").topLevelClass())
    }

    @Test
    fun `topLevelClass returns same class when no dollar sign`() {
        assertEquals(ClassName("com.example.MyService"), ClassName("com.example.MyService").topLevelClass())
    }

    @Test
    fun `topLevelClass handles deeply nested classes`() {
        assertEquals(ClassName("com.example.A"), ClassName("com.example.A\$B\$C\$D").topLevelClass())
    }

    // --- collapseLambda ---

    @Test
    fun `collapseLambda strips trailing numeric then function segments iteratively`() {
        assertEquals(ClassName("com.example.Controller"), ClassName("com.example.Controller\$handle\$1").collapseLambda())
    }

    @Test
    fun `collapseLambda handles multiple levels of lambda nesting`() {
        assertEquals(ClassName("com.example.Service"), ClassName("com.example.Service\$process\$1\$invoke\$2").collapseLambda())
    }

    @Test
    fun `collapseLambda stops when no more numeric segments match`() {
        assertEquals(ClassName("com.example.Outer\$Inner"), ClassName("com.example.Outer\$Inner").collapseLambda())
    }

    @Test
    fun `collapseLambda returns same class for non-lambda class`() {
        assertEquals(ClassName("com.example.MyService"), ClassName("com.example.MyService").collapseLambda())
    }

    // --- displayName ---

    @Test
    fun `displayName replaces dollar signs with dots`() {
        assertEquals("com.example.Outer.Inner", ClassName("com.example.Outer\$Inner").displayName())
    }

    @Test
    fun `displayName returns same value when no dollar signs`() {
        assertEquals("com.example.MyService", ClassName("com.example.MyService").displayName())
    }

    // --- stripPrefix ---

    @Test
    fun `stripPrefix removes matching prefix`() {
        assertEquals(
            ClassName("api.SetupKt"),
            ClassName("com.example.myapp.api.SetupKt").stripPackagePrefix(PackageName("com.example.myapp")),
        )
    }

    @Test
    fun `stripPrefix returns same class when prefix does not match`() {
        assertEquals(
            ClassName("org.other.Service"),
            ClassName("org.other.Service").stripPackagePrefix(PackageName("com.example.myapp")),
        )
    }

    @Test
    fun `stripPrefix returns same class when prefix is empty`() {
        assertEquals(
            ClassName("com.example.MyService"),
            ClassName("com.example.MyService").stripPackagePrefix(PackageName("")),
        )
    }

    // --- packagePath ---

    @Test
    fun `packagePath returns slash-separated package directory`() {
        assertEquals("com/example/service", ClassName("com.example.service.MyService").packagePath())
    }

    @Test
    fun `packagePath returns empty string for default package class`() {
        assertEquals("", ClassName("MyService").packagePath())
    }

    // --- isSyntheticName (companion) ---

    @Test
    fun `isSyntheticName detects anonymous class pattern in filename`() {
        assertTrue(ClassName.isSyntheticName("Foo\$1"))
    }

    @Test
    fun `isSyntheticName detects lambda pattern in filename`() {
        assertTrue(ClassName.isSyntheticName("Service\$lambda\$1"))
    }

    @Test
    fun `isSyntheticName returns false for regular class filename`() {
        assertFalse(ClassName.isSyntheticName("MyService"))
    }

    @Test
    fun `isSyntheticName returns false for named inner class`() {
        assertFalse(ClassName.isSyntheticName("Outer\$Inner"))
    }

    @Test
    fun `isSyntheticName detects numeric suffix mid-name`() {
        assertTrue(ClassName.isSyntheticName("Foo\$1\$bar"))
    }
}

class PackageNameTest {

    @Test
    fun `wraps a package name`() {
        val pkg = PackageName("com.example.service")

        assertEquals("com.example.service", pkg.value)
    }

    @Test
    fun `toString returns the package name`() {
        val pkg = PackageName("com.example.service")

        assertEquals("com.example.service", pkg.toString())
    }

    @Test
    fun `equality is based on value`() {
        val a = PackageName("com.example")
        val b = PackageName("com.example")

        assertEquals(a, b)
    }

    @Test
    fun `empty package name`() {
        val pkg = PackageName("")

        assertEquals("", pkg.value)
    }

    // --- matches ---

    @Test
    fun `matches returns true when regex matches package name`() {
        assertTrue(PackageName("com.example.service").matches(Regex("service")))
    }

    @Test
    fun `matches returns false when regex does not match`() {
        assertFalse(PackageName("com.example.service").matches(Regex("controller")))
    }

    // --- startsWith ---

    @Test
    fun `startsWith returns true for matching prefix`() {
        assertTrue(PackageName("com.example.service").startsWith("com.example"))
    }

    @Test
    fun `startsWith returns false for non-matching prefix`() {
        assertFalse(PackageName("com.example.service").startsWith("com.other"))
    }

    @Test
    fun `startsWith with PackageName parameter`() {
        assertTrue(PackageName("com.example.service").startsWith(PackageName("com.example")))
    }

    // --- isEmpty / isNotEmpty ---

    @Test
    fun `isEmpty returns true for empty package`() {
        assertTrue(PackageName("").isEmpty())
    }

    @Test
    fun `isEmpty returns false for non-empty package`() {
        assertFalse(PackageName("com.example").isEmpty())
    }

    @Test
    fun `isNotEmpty returns true for non-empty package`() {
        assertTrue(PackageName("com.example").isNotEmpty())
    }

    @Test
    fun `isNotEmpty returns false for empty package`() {
        assertFalse(PackageName("").isNotEmpty())
    }

    // --- truncate ---

    @Test
    fun `truncate strips root prefix and limits depth`() {
        val pkg = PackageName("com.example.api.rest.v1")

        assertEquals(PackageName("api.rest"), pkg.truncate(PackageName("com.example"), 2))
    }

    @Test
    fun `truncate with depth 1 keeps only first segment after prefix`() {
        val pkg = PackageName("com.example.api.rest.v1")

        assertEquals(PackageName("api"), pkg.truncate(PackageName("com.example"), 1))
    }

    @Test
    fun `truncate without prefix keeps full segments up to depth`() {
        val pkg = PackageName("com.example.api")

        assertEquals(PackageName("com.example.api"), pkg.truncate(PackageName(""), 3))
    }

    @Test
    fun `truncate with empty prefix and low depth truncates from start`() {
        val pkg = PackageName("com.example.api")

        assertEquals(PackageName("com.example"), pkg.truncate(PackageName(""), 2))
    }
}

class AnnotationNameTest {

    @Test
    fun `stores full FQN as value`() {
        val annotation = AnnotationName("org.springframework.web.bind.annotation.GetMapping")

        assertEquals("org.springframework.web.bind.annotation.GetMapping", annotation.value)
    }

    @Test
    fun `simpleName returns last segment after dot`() {
        val annotation = AnnotationName("org.springframework.web.bind.annotation.GetMapping")

        assertEquals("GetMapping", annotation.simpleName())
    }

    @Test
    fun `simpleName returns full value when no dots`() {
        val annotation = AnnotationName("Deprecated")

        assertEquals("Deprecated", annotation.simpleName())
    }

    @Test
    fun `packageName returns everything before last dot`() {
        val annotation = AnnotationName("org.springframework.web.bind.annotation.GetMapping")

        assertEquals("org.springframework.web.bind.annotation", annotation.packageName())
    }

    @Test
    fun `packageName returns empty string when no dots`() {
        val annotation = AnnotationName("Deprecated")

        assertEquals("", annotation.packageName())
    }

    @Test
    fun `toString returns the full FQN`() {
        val annotation = AnnotationName("org.springframework.stereotype.Service")

        assertEquals("org.springframework.stereotype.Service", annotation.toString())
    }

    @Test
    fun `comparable sorts alphabetically by FQN`() {
        val a = AnnotationName("jakarta.persistence.Entity")
        val b = AnnotationName("org.springframework.stereotype.Service")

        assertTrue(a < b)
    }

    @Test
    fun `matches regex against full FQN`() {
        val annotation = AnnotationName("org.springframework.web.bind.annotation.GetMapping")

        assertTrue(annotation.matches(Regex("GetMapping")))
        assertTrue(annotation.matches(Regex("springframework")))
        assertFalse(annotation.matches(Regex("PostMapping")))
    }

    // --- isInternal ---

    @Test
    fun `isInternal returns true for kotlin Metadata`() {
        assertTrue(AnnotationName("kotlin.Metadata").isInternal())
    }

    @Test
    fun `isInternal returns true for SourceDebugExtension`() {
        assertTrue(AnnotationName("kotlin.jvm.internal.SourceDebugExtension").isInternal())
    }

    @Test
    fun `isInternal returns true for DebugMetadata`() {
        assertTrue(AnnotationName("kotlin.coroutines.jvm.internal.DebugMetadata").isInternal())
    }

    @Test
    fun `isInternal returns false for user annotations`() {
        assertFalse(AnnotationName("org.springframework.stereotype.Service").isInternal())
        assertFalse(AnnotationName("kotlin.Deprecated").isInternal())
    }
}

class MethodRefTest {

    @Test
    fun `isGenerated returns true for synthetic access method`() {
        val ref = MethodRef(ClassName("com.example.Service"), "access\$doWork")

        assertTrue(ref.isGenerated())
    }

    @Test
    fun `isGenerated returns true for constructor`() {
        val ref = MethodRef(ClassName("com.example.Service"), "<init>")

        assertTrue(ref.isGenerated())
    }

    @Test
    fun `isGenerated returns false for regular method`() {
        val ref = MethodRef(ClassName("com.example.Service"), "doWork")

        assertFalse(ref.isGenerated())
    }
}
