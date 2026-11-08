package no.f12.codenavigator.navigation.branches

/** What kind of choice a [Branch] is. */
enum class BranchKind {
    /** `if (c)` and `else if (c)` arms. */
    IF,
    ELSE,

    /** A `when` expression; its children are [WHEN_ENTRY] branches. */
    WHEN,
    WHEN_ENTRY,
    TRY,
    CATCH,
    FINALLY,

    /** Right side of `?:`, which runs only when the left side is null. */
    ELVIS,

    /** Selector of `x?.call()`, which runs only when `x` is not null. */
    SAFE_CALL,

    /** Body of `for`, `while` or `do … while`. */
    LOOP,
}

enum class ExitKind { RETURN, THROW }

/** One thing inside a function body, in source order. */
sealed interface BranchItem {
    val line: Int
}

/** Code that runs only under [label] (e.g. `if (user.isAdmin)`, `else`, `catch (e: IOException)`). */
data class Branch(
    val kind: BranchKind,
    val label: String,
    override val line: Int,
    val children: List<BranchItem>,
) : BranchItem

/** A call or property access by simple [name] (`repo.delete(id)` → `delete`, `user.isAdmin` → `isAdmin`, `Foo()` → `Foo`). */
data class CallSite(
    val name: String,
    override val line: Int,
    /** True for a property read or write, which compiles to a getter or setter. */
    val property: Boolean = false,
) : BranchItem

data class Exit(
    val kind: ExitKind,
    /** Source text, shortened (e.g. `throw ForbiddenException("…")`). */
    val text: String,
    override val line: Int,
) : BranchItem

/**
 * The branch structure of one Kotlin function, with no compiler types, so it can cross the Gradle worker
 * boundary and be tested on its own.
 */
data class FunctionBranches(
    /** JVM name of the class that holds the function (`com.example.Foo`, `com.example.Foo$Inner`, or `com.example.FileKt` for top-level functions). */
    val className: String,
    val name: String,
    val startLine: Int,
    val endLine: Int,
    val body: List<BranchItem>,
)
