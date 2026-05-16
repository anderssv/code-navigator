package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.TypeMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TypeMatcherTest {

    @Test
    fun `fromPattern produces ExactMatcher for FQN patterns`() {
        val matcher = TypeMatcher.fromPattern("com.example.Service")

        assertIs<TypeMatcher.ExactMatcher>(matcher)
    }

    @Test
    fun `fromPattern produces RegexMatcher for short name patterns`() {
        val matcher = TypeMatcher.fromPattern("Service")

        assertIs<TypeMatcher.RegexMatcher>(matcher)
    }

    @Test
    fun `fromPattern produces RegexMatcher for patterns with regex metacharacters`() {
        val matcher = TypeMatcher.fromPattern("com.example.*Service")

        assertIs<TypeMatcher.RegexMatcher>(matcher)
    }

    @Test
    fun `ExactMatcher matches exact class name`() {
        val matcher = TypeMatcher.ExactMatcher(ClassName("com.example.Service"))

        assertEquals(true, matcher.matches(ClassName("com.example.Service")))
    }

    @Test
    fun `ExactMatcher matches inner classes`() {
        val matcher = TypeMatcher.ExactMatcher(ClassName("com.example.Service"))

        assertEquals(true, matcher.matches(ClassName("com.example.Service\$Inner")))
        assertEquals(true, matcher.matches(ClassName("com.example.Service\$Companion")))
    }

    @Test
    fun `ExactMatcher does not match longer class names`() {
        val matcher = TypeMatcher.ExactMatcher(ClassName("com.example.Service"))

        assertEquals(false, matcher.matches(ClassName("com.example.ServiceImpl")))
        assertEquals(false, matcher.matches(ClassName("com.example.ServiceResult")))
    }

    @Test
    fun `ExactMatcher does not match different packages`() {
        val matcher = TypeMatcher.ExactMatcher(ClassName("com.example.Service"))

        assertEquals(false, matcher.matches(ClassName("com.other.Service")))
    }

    @Test
    fun `RegexMatcher matches substring with containsMatchIn`() {
        val matcher = TypeMatcher.RegexMatcher(Regex("Service", RegexOption.IGNORE_CASE))

        assertEquals(true, matcher.matches(ClassName("com.example.Service")))
        assertEquals(true, matcher.matches(ClassName("com.example.ServiceImpl")))
        assertEquals(true, matcher.matches(ClassName("com.example.MyService")))
    }

    @Test
    fun `RegexMatcher is case-insensitive`() {
        val matcher = TypeMatcher.RegexMatcher(Regex("service", RegexOption.IGNORE_CASE))

        assertEquals(true, matcher.matches(ClassName("com.example.Service")))
    }

    @Test
    fun `RegexMatcher does not match unrelated classes`() {
        val matcher = TypeMatcher.RegexMatcher(Regex("Service", RegexOption.IGNORE_CASE))

        assertEquals(false, matcher.matches(ClassName("com.example.Repository")))
    }

    @Test
    fun `SetMatcher matches classes in the set`() {
        val matcher = TypeMatcher.SetMatcher(setOf(
            ClassName("com.example.Foo"),
            ClassName("com.example.Bar"),
        ))

        assertEquals(true, matcher.matches(ClassName("com.example.Foo")))
        assertEquals(true, matcher.matches(ClassName("com.example.Bar")))
        assertEquals(false, matcher.matches(ClassName("com.example.Baz")))
    }

    @Test
    fun `SetMatcher matches inner classes of targets`() {
        val matcher = TypeMatcher.SetMatcher(setOf(ClassName("com.example.Foo")))

        assertEquals(true, matcher.matches(ClassName("com.example.Foo\$Inner")))
        assertEquals(false, matcher.matches(ClassName("com.example.FooBar")))
    }

    @Test
    fun `resolve with FQN returns exact match`() {
        val all = listOf(
            ClassName("com.example.Service"),
            ClassName("com.example.ServiceImpl"),
            ClassName("com.example.Other"),
        )

        val resolved = TypeMatcher.resolve("com.example.Service", all)

        assertEquals(setOf(ClassName("com.example.Service")), resolved)
    }

    @Test
    fun `resolve with short name returns all matching classes`() {
        val all = listOf(
            ClassName("com.example.MyService"),
            ClassName("com.example.OtherService"),
            ClassName("com.example.Repository"),
        )

        val resolved = TypeMatcher.resolve("Service", all)

        assertEquals(setOf(ClassName("com.example.MyService"), ClassName("com.example.OtherService")), resolved)
    }
}
