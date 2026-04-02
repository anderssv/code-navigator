package no.f12.codenavigator

import no.f12.codenavigator.formatting.TableFormatter
import no.f12.codenavigator.navigation.classinfo.ClassInfo
import no.f12.codenavigator.navigation.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableFormatterTest {

    @Test
    fun `formats single class as aligned table`() {
        val classes = listOf(
            ClassInfo(
                className = ClassName("com.example.MyService"),
                sourceFileName = "MyService.kt",
                reconstructedSourcePath = "com/example/MyService.kt",
                isUserDefinedClass = true,
            ),
        )

        val output = TableFormatter.format(classes)

        assertTrue(output.contains("com.example.MyService"))
        assertTrue(output.contains("com/example/MyService.kt"))
        assertTrue(output.contains("Class"))
        assertTrue(output.contains("Source File"))
    }

    @Test
    fun `aligns columns based on longest entry`() {
        val classes = listOf(
            ClassInfo(ClassName("a.B"), "B.kt", "a/B.kt", true),
            ClassInfo(ClassName("com.example.very.long.ClassName"), "ClassName.kt", "com/example/very/long/ClassName.kt", true),
        )

        val lines = TableFormatter.format(classes).lines()
        val headerLine = lines.first()
        val separatorLine = lines[1]

        assertTrue(headerLine.contains("Class"))
        assertTrue(separatorLine.all { it == '-' || it == '|' || it == ' ' })
    }

    @Test
    fun `returns message for empty class list`() {
        val output = TableFormatter.format(emptyList())

        assertEquals("No classes found.", output)
    }

    @Test
    fun `shows total count at the bottom`() {
        val classes = listOf(
            ClassInfo(ClassName("a.B"), "B.kt", "a/B.kt", true),
            ClassInfo(ClassName("a.C"), "C.kt", "a/C.kt", true),
        )

        val output = TableFormatter.format(classes)

        assertTrue(output.contains("2 classes"))
    }
}
