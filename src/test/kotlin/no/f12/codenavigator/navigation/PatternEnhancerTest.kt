package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.PatternEnhancer
import kotlin.test.Test
import kotlin.test.assertEquals

class PatternEnhancerTest {

    @Test
    fun `splits camel-case pattern into parts joined by dot-star`() {
        val result = PatternEnhancer.enhance("PollService")

        assertEquals("Poll.*Service", result)
    }

    @Test
    fun `single word pattern stays unchanged`() {
        val result = PatternEnhancer.enhance("Service")

        assertEquals("Service", result)
    }

    @Test
    fun `pattern with regex metacharacters passes through unchanged`() {
        assertEquals("Poll.*Service", PatternEnhancer.enhance("Poll.*Service"))
        assertEquals("Poll.+Service", PatternEnhancer.enhance("Poll.+Service"))
        assertEquals("Poll|Service", PatternEnhancer.enhance("Poll|Service"))
        assertEquals("[Pp]oll", PatternEnhancer.enhance("[Pp]oll"))
        assertEquals("Poll(Creation)?Service", PatternEnhancer.enhance("Poll(Creation)?Service"))
    }

    @Test
    fun `all-lowercase pattern stays unchanged`() {
        assertEquals("service", PatternEnhancer.enhance("service"))
    }

    @Test
    fun `multiple camel-case parts split correctly`() {
        assertEquals("My.*Poll.*Creation.*Service", PatternEnhancer.enhance("MyPollCreationService"))
    }

    @Test
    fun `pattern with dots passes through unchanged`() {
        assertEquals("com.example.Service", PatternEnhancer.enhance("com.example.Service"))
    }

    @Test
    fun `pattern with only uppercase letters stays unchanged`() {
        assertEquals("ABC", PatternEnhancer.enhance("ABC"))
    }

    @Test
    fun `empty pattern stays unchanged`() {
        assertEquals("", PatternEnhancer.enhance(""))
    }

    @Test
    fun `enhanced pattern still matches original class name via containsMatchIn`() {
        val enhanced = PatternEnhancer.enhance("UserService")
        val regex = Regex(enhanced, RegexOption.IGNORE_CASE)

        assertEquals(true, regex.containsMatchIn("com.example.UserService"))
        assertEquals(true, regex.containsMatchIn("com.example.UserCreationService"))
        assertEquals(false, regex.containsMatchIn("com.example.OrderService"))
    }

    @Test
    fun `stopword And in camel case becomes optional`() {
        val enhanced = PatternEnhancer.enhance("TermsAndConditionsService")

        assertEquals("Terms(?:And)?.*Conditions.*Service", enhanced)
    }

    @Test
    fun `stopword Or in camel case becomes optional`() {
        assertEquals("Read(?:Or)?.*Write.*Lock", PatternEnhancer.enhance("ReadOrWriteLock"))
    }

    @Test
    fun `stopword Of in camel case becomes optional`() {
        assertEquals("List(?:Of)?.*Items.*Provider", PatternEnhancer.enhance("ListOfItemsProvider"))
    }

    @Test
    fun `enhanced stopword pattern still matches original class name`() {
        val enhanced = PatternEnhancer.enhance("TermsAndConditionsService")
        val regex = Regex(enhanced, RegexOption.IGNORE_CASE)

        assertEquals(true, regex.containsMatchIn("TermsAndConditionsService"), "Should match exact name")
        assertEquals(true, regex.containsMatchIn("TermsConditionsService"), "Should match without stopword")
        assertEquals(true, regex.containsMatchIn("TermsAndSpecialConditionsService"), "Should match with extra words")
        assertEquals(false, regex.containsMatchIn("TermsService"), "Should not match without Conditions")
    }

    @Test
    fun `multiple consecutive stopwords become optional`() {
        val enhanced = PatternEnhancer.enhance("ReadAndOrWriteLock")

        assertEquals("Read(?:And)?(?:Or)?.*Write.*Lock", enhanced)
    }

    @Test
    fun `looksLikeFqn returns true for dotted names without metacharacters`() {
        assertEquals(true, PatternEnhancer.looksLikeFqn("com.example.Service"))
        assertEquals(true, PatternEnhancer.looksLikeFqn("no.f12.MyClass"))
    }

    @Test
    fun `looksLikeFqn returns false for patterns without dots`() {
        assertEquals(false, PatternEnhancer.looksLikeFqn("Service"))
        assertEquals(false, PatternEnhancer.looksLikeFqn("MyService"))
    }

    @Test
    fun `looksLikeFqn returns false for patterns with regex metacharacters`() {
        assertEquals(false, PatternEnhancer.looksLikeFqn("com.example.*Service"))
        assertEquals(false, PatternEnhancer.looksLikeFqn("com.example.Service|Other"))
    }

    @Test
    fun `escapeForExactMatch produces regex that matches literal class and inner classes only`() {
        val escaped = PatternEnhancer.escapeForExactMatch("com.example.Service")
        val regex = Regex(escaped, RegexOption.IGNORE_CASE)

        assertEquals(true, regex.containsMatchIn("com.example.Service"))
        assertEquals(true, regex.containsMatchIn("com.example.Service\$Inner"))
        assertEquals(false, regex.containsMatchIn("comXexampleXService"))
        assertEquals(false, regex.containsMatchIn("com.example.ServiceImpl"))
        assertEquals(false, regex.containsMatchIn("com.example.ServiceResult"))
    }
}
