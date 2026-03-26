package no.f12.codenavigator.navigation

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
}
