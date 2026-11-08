package no.f12.codenavigator.navigation.branches

import kotlin.test.Test
import kotlin.test.assertEquals

/** Branch detection on Kotlin source snippets. Expectations use [render], a test-only outline of the result. */
class BranchScannerTest {

    private fun scan(source: String, fileName: String = "Svc.kt") = BranchScanner.scanSource(fileName, source.trimIndent())

    /** Body of function [name] in class `p.Svc`, wrapped from [members]. */
    private fun body(members: String, name: String = "f"): String {
        val source = "package p\n\nclass Svc {\n" + members.trimIndent() + "\n}\n"
        val function = BranchScanner.scanSource("Svc.kt", source).single { it.className == "p.Svc" && it.name == name }
        return render(function.body)
    }

    private fun render(items: List<BranchItem>, indent: String = ""): String = items.joinToString("\n") { item ->
        when (item) {
            is CallSite -> "$indent${if (item.property) "prop" else "call"} ${item.name}"
            is Exit -> "$indent${item.text}"
            is Branch -> if (item.children.isEmpty()) "$indent${item.label}" else "$indent${item.label}\n${render(item.children, "$indent  ")}"
        }
    }

    // --- Plain code ---

    @Test
    fun `calls in source order with no branches`() {
        assertEquals(
            """
            call load
            call validate
            call save
            """.trimIndent(),
            body("""
                fun f() {
                    val x = repo.load()
                    validate(x)
                    repo.save(x)
                }
            """),
        )
    }

    @Test
    fun `arguments are evaluated before the call that takes them`() {
        assertEquals("call b\ncall a", body("fun f() { a(b()) }"))
    }

    // --- if ---

    @Test
    fun `if without else - calls in the condition stay outside the branch`() {
        assertEquals(
            """
            call isAdmin
            if (auth.isAdmin(user))
              call delete
            """.trimIndent(),
            body("""
                fun f() {
                    if (auth.isAdmin(user)) {
                        repo.delete(id)
                    }
                }
            """),
        )
    }

    @Test
    fun `if and else`() {
        assertEquals(
            """
            if (a)
              call x
            else
              call y
            """.trimIndent(),
            body("fun f() { if (a) { x() } else { y() } }"),
        )
    }

    @Test
    fun `else-if chain is flattened, with each later condition inside its own arm`() {
        assertEquals(
            """
            if (a)
              call x
            else if (b())
              call b
              call y
            else
              call z
            """.trimIndent(),
            body("""
                fun f() {
                    if (a) {
                        x()
                    } else if (b()) {
                        y()
                    } else {
                        z()
                    }
                }
            """),
        )
    }

    @Test
    fun `nested if inside else`() {
        assertEquals(
            """
            if (a)
              call x
            else
              call before
              if (b)
                call y
            """.trimIndent(),
            body("""
                fun f() {
                    if (a) {
                        x()
                    } else {
                        before()
                        if (b) {
                            y()
                        }
                    }
                }
            """),
        )
    }

    @Test
    fun `single-line if and else without braces`() {
        assertEquals("if (a)\n  call x\nelse\n  call y", body("fun f() { if (a) x() else y() }"))
    }

    @Test
    fun `if used as an expression and as an expression body`() {
        val expected = "if (a)\n  call x\nelse\n  call y"

        assertEquals(expected, body("fun f() { val v = if (a) x() else y() }"))
        assertEquals(expected, body("fun f() = if (a) x() else y()"))
    }

    @Test
    fun `empty branch is kept so the condition is visible`() {
        assertEquals("if (a)\ncall x", body("fun f() { if (a) { }\n x() }"))
    }

    // --- when ---

    @Test
    fun `when with a subject, several conditions per entry and else`() {
        assertEquals(
            """
            call type
            when (order.type())
              PRIORITY, URGENT ->
                call enqueueFast
              else ->
                call enqueue
            """.trimIndent(),
            body("""
                fun f() {
                    when (order.type()) {
                        PRIORITY, URGENT -> queue.enqueueFast(order)
                        else -> queue.enqueue(order)
                    }
                }
            """),
        )
    }

    @Test
    fun `when without a subject keeps condition calls inside each entry`() {
        assertEquals(
            """
            when
              isEmpty() ->
                call isEmpty
                return
              else ->
                call process
            """.trimIndent(),
            body("""
                fun f() {
                    when {
                        isEmpty() -> return
                        else -> process()
                    }
                }
            """),
        )
    }

    @Test
    fun `try inside a when entry`() {
        assertEquals(
            """
            when (x)
              1 ->
                try
                  call risky
                catch (e: Exception)
                  call log
            """.trimIndent(),
            body("""
                fun f() {
                    when (x) {
                        1 -> {
                            try { risky() } catch (e: Exception) { log(e) }
                        }
                    }
                }
            """),
        )
    }

    // --- try ---

    @Test
    fun `try with several catches and finally`() {
        assertEquals(
            """
            try
              call charge
            catch (e: TimeoutException)
              call retry
            catch (e: PaymentException)
              throw e
            finally
              call close
            """.trimIndent(),
            body("""
                fun f() {
                    try {
                        gateway.charge(amount)
                    } catch (e: TimeoutException) {
                        retry()
                    } catch (e: PaymentException) {
                        throw e
                    } finally {
                        conn.close()
                    }
                }
            """),
        )
    }

    // --- Null checks ---

    @Test
    fun `elvis with return, throw and a fallback call`() {
        assertEquals(
            """
            call find
            if (repo.find(id) == null)
              return
            call current
            if (session.current() == null)
              call IllegalStateException
              throw IllegalStateException("no session")
            call cached
            if (cache.cached() == null)
              call load
            """.trimIndent(),
            body("""
                fun f() {
                    val user = repo.find(id) ?: return
                    val s = session.current() ?: throw IllegalStateException("no session")
                    val v = cache.cached() ?: loader.load()
                }
            """),
        )
    }

    @Test
    fun `chained safe calls are sibling branches`() {
        assertEquals(
            """
            if (user != null)
              call profile
            if (user?.profile() != null)
              call email
            """.trimIndent(),
            body("fun f() { user?.profile()?.email() }"),
        )
    }

    @Test
    fun `safe call with a let block`() {
        assertEquals(
            """
            if (user != null)
              call let
              call notify
            """.trimIndent(),
            body("fun f() { user?.let { mailer.notify(it) } }"),
        )
    }

    // --- Loops ---

    @Test
    fun `for, while and do-while`() {
        assertEquals(
            """
            call items
            for (item in order.items())
              call save
            while (queue.hasNext())
              call hasNext
              call next
            do … while (more())
              call step
              call more
            """.trimIndent(),
            body("""
                fun f() {
                    for (item in order.items()) {
                        repo.save(item)
                    }
                    while (queue.hasNext()) {
                        queue.next()
                    }
                    do {
                        step()
                    } while (more())
                }
            """),
        )
    }

    // --- Exits ---

    @Test
    fun `return with a value, and a long throw is shortened`() {
        assertEquals(
            """
            if (bad)
              call IllegalArgumentException
              throw IllegalArgumentException("this message is long enough that the …
            call compute
            return compute(x)
            """.trimIndent(),
            body("""
                fun f(): Int {
                    if (bad) throw IllegalArgumentException("this message is long enough that the exit text gets cut off at some point")
                    return compute(x)
                }
            """),
        )
    }

    // --- Lambdas, properties, constructors ---

    @Test
    fun `lambda bodies are kept inline after the call that takes them`() {
        assertEquals(
            """
            call forEach
            if (it > 0)
              call save
            """.trimIndent(),
            body("fun f() { list.forEach { if (it > 0) repo.save(it) } }"),
        )
    }

    @Test
    fun `property reads and writes are property sites`() {
        assertEquals(
            """
            prop isAdmin
            if (user.isAdmin)
              prop name
            """.trimIndent(),
            body("fun f() { if (user.isAdmin) { user.name = \"x\" } }"),
        )
    }

    @Test
    fun `constructor calls use the class name`() {
        assertEquals("call Order\ncall save", body("fun f() { repo.save(Order(1)) }"))
    }

    @Test
    fun `local functions and object literals are not part of the body`() {
        assertEquals(
            "call a\ncall b",
            body("""
                fun f() {
                    a()
                    fun local() { hidden() }
                    val o = object : Runnable { override fun run() { alsoHidden() } }
                    b()
                }
            """),
        )
    }

    // --- Function identity and lines ---

    @Test
    fun `class names follow the JVM - top-level, nested, companion and object`() {
        val functions = scan(
            """
            package p

            fun top() {}

            class Outer {
                fun member() {}
                class Inner { fun nested() {} }
                companion object { fun create() {} }
            }

            object Registry { fun register() {} }
            """,
            fileName = "Things.kt",
        )

        assertEquals(
            listOf("p.ThingsKt.top", "p.Outer.member", "p.Outer\$Inner.nested", "p.Outer\$Companion.create", "p.Registry.register"),
            functions.map { "${it.className}.${it.name}" },
        )
    }

    @Test
    fun `functions and branches carry line numbers`() {
        val function = scan(
            """
            package p

            class Svc {
                fun f() {
                    if (a) {
                        x()
                    }
                }
            }
            """,
        ).single()

        assertEquals(4, function.startLine)
        assertEquals(8, function.endLine)
        val branch = function.body.single() as Branch
        assertEquals(5, branch.line)
        assertEquals(6, branch.children.single().line)
    }

    @Test
    fun `long conditions are put on one line and shortened`() {
        val label = (scan(
            """
            package p

            class Svc {
                fun f() {
                    if (order.customer.isPremium() &&
                        order.total > settings.premiumThreshold &&
                        featureFlags.isEnabled("premium-discounts")) {
                        discount()
                    }
                }
            }
            """,
        ).single().body.last() as Branch).label

        assertEquals("if (order.customer.isPremium() && order.total > settings.premiumThreshold && featur…)", label)
    }
}
