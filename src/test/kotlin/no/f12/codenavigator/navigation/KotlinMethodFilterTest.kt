package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinMethodFilterTest {

    @Test
    fun `excludes constructors`() {
        assertTrue(KotlinMethodFilter.isGenerated("<init>"))
    }

    @Test
    fun `excludes static initializers`() {
        assertTrue(KotlinMethodFilter.isGenerated("<clinit>"))
    }

    @Test
    fun `excludes data class methods - toString hashCode equals copy`() {
        assertTrue(KotlinMethodFilter.isGenerated("toString"))
        assertTrue(KotlinMethodFilter.isGenerated("hashCode"))
        assertTrue(KotlinMethodFilter.isGenerated("equals"))
        assertTrue(KotlinMethodFilter.isGenerated("copy"))
    }

    @Test
    fun `excludes data class componentN methods`() {
        assertTrue(KotlinMethodFilter.isGenerated("component1"))
        assertTrue(KotlinMethodFilter.isGenerated("component2"))
        assertTrue(KotlinMethodFilter.isGenerated("component99"))
    }

    @Test
    fun `excludes copy dollar default`() {
        assertTrue(KotlinMethodFilter.isGenerated("copy\$default"))
    }

    @Test
    fun `excludes access dollar bridge methods`() {
        assertTrue(KotlinMethodFilter.isGenerated("access\$getDb\$p"))
        assertTrue(KotlinMethodFilter.isGenerated("access\$setName"))
    }

    @Test
    fun `excludes lambda methods`() {
        assertTrue(KotlinMethodFilter.isGenerated("invoke\$lambda\$0"))
    }

    @Test
    fun `allows normal user-defined methods`() {
        assertFalse(KotlinMethodFilter.isGenerated("process"))
        assertFalse(KotlinMethodFilter.isGenerated("findUser"))
        assertFalse(KotlinMethodFilter.isGenerated("handleRequest"))
    }

    @Test
    fun `excludes enum generated methods`() {
        assertTrue(KotlinMethodFilter.isGenerated("\$values"))
        assertTrue(KotlinMethodFilter.isGenerated("valueOf"))
        assertTrue(KotlinMethodFilter.isGenerated("values"))
    }

    @Test
    fun `excludes inline value class generated methods`() {
        assertTrue(KotlinMethodFilter.isGenerated("box-impl"))
        assertTrue(KotlinMethodFilter.isGenerated("unbox-impl"))
        assertTrue(KotlinMethodFilter.isGenerated("equals-impl"))
        assertTrue(KotlinMethodFilter.isGenerated("hashCode-impl"))
        assertTrue(KotlinMethodFilter.isGenerated("toString-impl"))
        assertTrue(KotlinMethodFilter.isGenerated("equals-impl0"))
    }

    @Test
    fun `excludes main entry point`() {
        assertTrue(KotlinMethodFilter.isGenerated("main"))
    }
}
