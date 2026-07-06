package no.f12.codenavigator.config

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CnavConfigTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writeConfig(json: String): File {
        val file = tempDir.resolve("cnav-config.json").toFile()
        file.writeText(json)
        return tempDir.toFile()
    }

    @Test
    fun `no config file returns empty defaults`() {
        val defaults = CnavConfig.loadDefaults(tempDir.toFile())

        assertTrue(defaults.isEmpty())
    }

    @Test
    fun `config file without defaults section returns empty defaults`() {
        val dir = writeConfig("""{"hints": {"port": ["*Repository"]}}""")

        val defaults = CnavConfig.loadDefaults(dir)

        assertTrue(defaults.isEmpty())
    }

    @Test
    fun `parses string and boolean defaults`() {
        val dir = writeConfig(
            """{"defaults": {"format": "llm", "scope": "prod", "include-external": true}}""",
        )

        val defaults = CnavConfig.loadDefaults(dir)

        assertEquals("llm", defaults["format"])
        assertEquals("prod", defaults["scope"])
        assertEquals("true", defaults["include-external"])
    }

    @Test
    fun `stringifies list defaults as comma-joined`() {
        val dir = writeConfig(
            """{"defaults": {"exclude-annotated": ["Deprecated", "Generated"]}}""",
        )

        val defaults = CnavConfig.loadDefaults(dir)

        assertEquals("Deprecated,Generated", defaults["exclude-annotated"])
    }

    @Test
    fun `applyDefaults merges config defaults under explicit properties`() {
        val dir = writeConfig("""{"defaults": {"format": "llm", "scope": "prod"}}""")

        val result = CnavConfig.applyDefaults(mapOf("scope" to "test"), dir)

        assertEquals("llm", result["format"], "Config default should fill in the missing key")
        assertEquals("test", result["scope"], "Explicit property should win over the config default")
    }

    @Test
    fun `applyDefaults returns properties unchanged when no config file exists`() {
        val properties = mapOf("format" to "text")

        val result = CnavConfig.applyDefaults(properties, tempDir.toFile())

        assertEquals(properties, result)
    }
}
