package no.f12.codenavigator.navigation.refactor

import org.openrewrite.ExecutionContext
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.kotlin.KotlinParser
import org.openrewrite.SourceFile
import org.openrewrite.kotlin.KotlinIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.tree.K
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class OpenRewriteApiExplorationTest {

    @Test
    fun `can parse a Kotlin file and visit method declarations`() {
        val source = """
            package com.example

            class Greeter {
                fun greet(name: String): String = "Hello, ${'$'}name!"
            }
        """.trimIndent()

        val parser = KotlinParser.builder().build()
        val ctx = InMemoryExecutionContext { it.printStackTrace() }
        val parsed = parser.parse(ctx, source).toList()

        assertTrue(parsed.isNotEmpty(), "Should parse at least one source file")

        val methodNames = mutableListOf<String>()
        val classNames = mutableListOf<String>()
        val paramNames = mutableListOf<String>()

        val visitor = object : KotlinIsoVisitor<ExecutionContext>() {
            override fun visitClassDeclaration(
                classDecl: J.ClassDeclaration,
                ctx: ExecutionContext,
            ): J.ClassDeclaration {
                classNames.add(classDecl.simpleName)
                classDecl.type?.fullyQualifiedName?.let { classNames.add("fqn:$it") }
                return super.visitClassDeclaration(classDecl, ctx)
            }

            override fun visitMethodDeclaration(
                method: J.MethodDeclaration,
                ctx: ExecutionContext,
            ): J.MethodDeclaration {
                methodNames.add(method.simpleName)
                method.parameters.forEach { p ->
                    when (p) {
                        is J.VariableDeclarations -> p.variables.forEach { v ->
                            paramNames.add(v.simpleName)
                        }
                        else -> paramNames.add("other:${p.javaClass.simpleName}")
                    }
                }
                return super.visitMethodDeclaration(method, ctx)
            }
        }

        visitor.visit(parsed[0], ctx)

        assertTrue(classNames.any { it == "Greeter" }, "Should find class: $classNames")
        assertTrue(methodNames.any { it == "greet" }, "Should find method: $methodNames")
        assertTrue(paramNames.any { it == "name" }, "Should find param: $paramNames")
    }

    @Test
    fun `can rename a parameter via visitor`() {
        val source = """
            package com.example

            class Greeter {
                fun greet(name: String): String = "Hello, ${'$'}name!"
            }
        """.trimIndent()

        val parser = KotlinParser.builder().build()
        val ctx = InMemoryExecutionContext { it.printStackTrace() }
        val parsed = parser.parse(ctx, source).toList()

        val visitor = object : KotlinIsoVisitor<ExecutionContext>() {
            override fun visitMethodDeclaration(
                method: J.MethodDeclaration,
                ctx: ExecutionContext,
            ): J.MethodDeclaration {
                var m = super.visitMethodDeclaration(method, ctx)
                if (m.simpleName != "greet") return m

                val newParams = m.parameters.map { param ->
                    if (param is J.VariableDeclarations) {
                        val vars = param.variables
                        if (vars.size == 1 && vars[0].simpleName == "name") {
                            param.withVariables(
                                vars.map { v -> v.withName(v.name.withSimpleName("person")) },
                            )
                        } else param
                    } else param
                }
                return m.withParameters(newParams)
            }
        }

        val modified = visitor.visit(parsed[0], ctx) as SourceFile
        val after = modified.printAll()

        assertTrue(after.contains("person: String"), "Should contain renamed param. Got:\n$after")
        assertTrue(!after.contains("name: String"), "Should not contain old param name. Got:\n$after")
    }

    @Test
    fun `dump AST tree structure for Kotlin source`() {
        val source = """
            package com.example

            class Greeter {
                fun greet(name: String): String = "Hello, ${'$'}name!"
            }
        """.trimIndent()

        val parser = KotlinParser.builder().build()
        val ctx = InMemoryExecutionContext { it.printStackTrace() }
        val parsed = parser.parse(ctx, source).toList()

        assertTrue(parsed.isNotEmpty(), "Should parse at least one source file")

        // Check what we actually got
        val sourceFile = parsed[0]
        val info = buildString {
            appendLine("Type: ${sourceFile.javaClass.name}")
            appendLine("Is SourceFile: ${sourceFile is SourceFile}")
            appendLine("Is J.CompilationUnit: ${sourceFile is J.CompilationUnit}")
            if (sourceFile is org.openrewrite.tree.ParseError) {
                appendLine("PARSE ERROR!")
                appendLine("Error text: ${sourceFile.text}")
                appendLine("Error erroneous: ${sourceFile.erroneous}")
                try {
                    throw sourceFile.toException()
                } catch (e: Exception) {
                    appendLine("Exception: ${e.message}")
                    appendLine("Cause: ${e.cause}")
                    appendLine("Cause message: ${e.cause?.message}")
                    appendLine("Stack: ${e.cause?.stackTraceToString()?.take(2000)}")
                }
            }
            if (sourceFile is J.CompilationUnit) {
                appendLine("Classes: ${sourceFile.classes.map { it.simpleName }}")
                appendLine("Package: ${sourceFile.packageDeclaration?.expression}")
            }
        }

        assertTrue(sourceFile !is org.openrewrite.tree.ParseError, "Parse failed!\n$info")

        val nodeTypes = mutableListOf<String>()

        val visitor = object : KotlinIsoVisitor<ExecutionContext>() {
            private var depth = 0

            override fun preVisit(tree: J, ctx: ExecutionContext): J? {
                val indent = "  ".repeat(depth)
                val typeName = tree.javaClass.simpleName
                val extra = when (tree) {
                    is J.ClassDeclaration -> " name=${tree.simpleName}"
                    is J.MethodDeclaration -> " name=${tree.simpleName}"
                    is J.Identifier -> " name=${tree.simpleName}"
                    is J.VariableDeclarations -> " vars=${tree.variables.map { it.simpleName }}"
                    else -> ""
                }
                nodeTypes.add("$indent$typeName$extra")
                depth++
                return super.preVisit(tree, ctx)
            }

            override fun postVisit(tree: J, ctx: ExecutionContext): J? {
                depth--
                return super.postVisit(tree, ctx)
            }
        }

        val result = visitor.visit(sourceFile, ctx)
        val resultInfo = "Result: ${result?.javaClass?.name}, nodeTypes count: ${nodeTypes.size}"

        val treeOutput = nodeTypes.joinToString("\n")
        assertTrue(nodeTypes.size > 1, "Should have AST nodes.\nParsed info: $info\nResult: $resultInfo\nTree:\n$treeOutput")
    }

    @Test
    fun `can parse from file paths and find FQN`() {
        val testFile = File("test-project/src/main/kotlin/com/example/services/AuditService.kt")
        assertTrue(testFile.exists(), "Test file should exist")

        val parser = KotlinParser.builder().build()
        val ctx = InMemoryExecutionContext { it.printStackTrace() }
        val parsed = parser.parse(listOf(testFile.toPath()), null, ctx).toList()

        assertTrue(parsed.isNotEmpty(), "Should parse the file")

        val classInfo = mutableListOf<String>()

        val visitor = object : KotlinIsoVisitor<ExecutionContext>() {
            override fun visitClassDeclaration(
                classDecl: J.ClassDeclaration,
                ctx: ExecutionContext,
            ): J.ClassDeclaration {
                classInfo.add("name=${classDecl.simpleName}, fqn=${classDecl.type?.fullyQualifiedName}")
                return super.visitClassDeclaration(classDecl, ctx)
            }
        }

        visitor.visit(parsed[0], ctx)

        assertTrue(classInfo.any { it.contains("AuditService") }, "Should find AuditService: $classInfo")
    }
}
