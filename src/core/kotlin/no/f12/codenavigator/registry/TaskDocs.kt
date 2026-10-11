package no.f12.codenavigator.registry

import no.f12.codenavigator.navigation.types.FrameworkPresets

/**
 * The documentation for every task, keyed by goal. See [TaskDoc] for the fields and
 * [DocTemplate] for the placeholder syntax. A test asserts every non-help task has an entry
 * with at least one `whenToUse` question and a schema, and that no entry is orphaned.
 */
object TaskDocs {

    fun of(goal: String): TaskDoc = byGoal[goal] ?: TaskDoc.NONE

    val byGoal: Map<String, TaskDoc> = mapOf(

        // ---------------------------------------------------------------- navigation

        "list-classes" to TaskDoc(
            details = listOf(
                "Lists all classes in the project and their source files. Scans both main and test source sets by default.",
            ),
            whenToUse = listOf("What classes exist in this project?", "What classes are in library JAR X?"),
            schema = listOf("""[{"className": "com.example.Foo", "sourceFile": "Foo.kt", "sourcePath": "com/example/Foo.kt"}]"""),
            caveats = listOf("Use {p:jar=<path-or-artifact>} to list the classes inside a dependency instead of the project."),
            related = listOf("find-class", "class-detail"),
        ),

        "find-class" to TaskDoc(
            details = listOf(
                "Searches for classes matching a regex pattern. Matches against both class name and source file path (case-insensitive).",
            ),
            whenToUse = listOf("Where is class X?", "Which classes are named like X?"),
            schema = listOf("""[{"className": "com.example.Foo", "sourceFile": "Foo.kt", "sourcePath": "com/example/Foo.kt"}]"""),
            related = listOf("find-symbol", "find-declaration", "class-detail"),
        ),

        "find-symbol" to TaskDoc(
            details = listOf(
                "Searches for symbols (methods, fields) matching a regex pattern. Matches against package, class name, symbol name and source file (case-insensitive).",
                "Filters out constructors, synthetic methods, property accessors and data class generated methods.",
            ),
            whenToUse = listOf("Which class declares method or field X?"),
            schema = listOf("""[{"package": "com.example", "class": "Service", "symbol": "doWork", "kind": "method", "sourceFile": "Service.kt"}]"""),
            caveats = listOf("Reports the declaring class and file, not the position inside it; use {t:find-declaration} for exact lines."),
            related = listOf("find-declaration", "find-callers", "class-detail"),
        ),

        "find-declaration" to TaskDoc(
            details = listOf(
                "Finds Kotlin declarations (classes, functions, properties, typealiases) by pattern and reports their exact source boundaries from the compiler's parser. No compilation needed.",
                "Each hit carries start/end line and column, 0-based character offsets, the line the name is on, and a signature so overloads can be told apart.",
                "Ranges include KDoc and annotations through the closing brace, so a declaration can be read, replaced or extracted without splitting on raw text.",
                "Unqualified patterns match the declaration name; patterns containing a '.' match the dotted FQN (Class.member, package prefix) or the source path.",
            ),
            whenToUse = listOf(
                "Where exactly is method X.m in the source? Which lines does it span?",
                "List every method of class X with its line range",
                "I am about to read or edit a method and need its boundaries",
            ),
            interpretation = listOf(
                "startLine/endLine cover the whole declaration including KDoc and annotations; nameLine is where the identifier sits. Read from nameLine when you only want the code.",
                "Offsets are 0-based over LF-normalised content with endOffset exclusive, so content.substring(startOffset, endOffset) is exactly the declaration text.",
                "Two hits with the same fqn are overloads; the signature tells them apart.",
            ),
            schema = listOf(
                """[{"kind": "function", "fqn": "com.example.Service.doWork", "name": "doWork", "signature": "doWork(id: String): Int",""",
                """  "file": "/abs/path/Service.kt", "sourcePath": "com/example/Service.kt",""",
                """  "startLine": 12, "startColumn": 5, "endLine": 18, "endColumn": 5, "startOffset": 300, "endOffset": 480, "nameLine": 13}]""",
            ),
            caveats = listOf(
                "All methods of one class: {p:pattern=\"OrderService\\\\..*\"} {p:kind=function}. The class itself plus every member: {p:pattern=\"OrderService(\\\\..*)?\"}.",
                "Kotlin sources only. Local declarations inside function bodies are not reported.",
            ),
            related = listOf("find-symbol", "class-detail", "find-class"),
        ),

        "class-detail" to TaskDoc(
            details = listOf(
                "Shows class signature: fields, methods, superclass, implemented interfaces and annotations.",
                "Filters out constructors, synthetic methods, property accessors and data class boilerplate.",
            ),
            whenToUse = listOf("What does class X look like?", "What does class X look like in a dependency JAR?"),
            schema = listOf(
                """[{"className": "...", "sourceFile": "...", "superClass": "...",""",
                """  "annotations": [{"name": "...", "parameters": {}}],""",
                """  "interfaces": ["..."], "fields": [{"name": "...", "type": "...", "annotations": [...]}],""",
                """  "methods": [{"name": "...", "parameters": ["..."], "returnType": "...", "annotations": [...]}]}]""",
            ),
            related = listOf("context", "find-declaration", "type-hierarchy"),
        ),

        "find-callers" to TaskDoc(
            details = listOf(
                "Full call hierarchy for a method: the complete tree of callers, recursively, through interfaces and inheritance.",
                "Builds a call graph from compiled code, then walks callers transitively up to maxdepth.",
                "Takes one combined \"Class.method\" pattern. {t:find-usages} answers a similar question with separate owner-class/method params.",
            ),
            whenToUse = listOf("Who calls method X?", "Show me the call hierarchy above X"),
            interpretation = listOf(
                "Kotlin inline functions have no call edges because the compiler copies the body into every call site. The task warns when the target is inline; read that as 'use grep for this one' rather than '(no callers)'.",
            ),
            schema = listOf(
                """[{"method": "com.example.Service.doWork", "sourceFile": "Service.kt",""",
                """  "children": [{"method": "...", "sourceFile": "...", "children": [...]}]}]""",
            ),
            caveats = listOf("{p:project-only=true} hides JDK/stdlib noise; raise {p:maxdepth=5} for deeper traversal."),
            related = listOf("find-callees", "find-usages", "context"),
        ),

        "find-callees" to TaskDoc(
            details = listOf(
                "Full call hierarchy for a method: the complete tree of callees, recursively.",
                "Builds a call graph from compiled code, then walks callees transitively up to maxdepth. Interface call sites are expanded to their implementors, capped by max-implementors.",
            ),
            whenToUse = listOf("What calls does method X make?", "What does class X depend on at runtime?"),
            schema = listOf(
                """[{"method": "com.example.Service.doWork", "sourceFile": "Service.kt",""",
                """  "children": [{"method": "...", "sourceFile": "...", "children": [...]}]}]""",
            ),
            related = listOf("find-callers", "context"),
        ),

        "find-interfaces" to TaskDoc(
            details = listOf(
                "Finds classes that implement a given interface. Builds an interface-to-implementors map from compiled code. Both main and test source sets are scanned by default, so test fakes are visible.",
            ),
            whenToUse = listOf("Who implements interface X?", "Does port X have a test fake?"),
            interpretation = listOf(
                "A port with a production adapter but no test implementor is a missing fake. Use {p:scope=test} to see only test implementations.",
            ),
            schema = listOf(
                """[{"interface": "com.example.Repository",""",
                """  "implementors": [{"className": "com.example.SqlRepo", "sourceFile": "SqlRepo.kt"}]}]""",
            ),
            related = listOf("type-hierarchy", "test-coupling"),
        ),

        "type-hierarchy" to TaskDoc(
            details = listOf(
                "Shows the type hierarchy for classes matching a pattern: supertypes upward (superclass chain and interfaces, recursively) and implementors downward.",
            ),
            whenToUse = listOf("What does class X extend or implement?"),
            schema = listOf(
                """[{"className": "com.example.SqlRepo", "sourceFile": "SqlRepo.kt",""",
                """  "supertypes": [{"className": "com.example.Repository", "kind": "INTERFACE", "supertypes": []}],""",
                """  "implementors": []}]""",
            ),
            related = listOf("find-interfaces", "class-detail"),
        ),

        "package-deps" to TaskDoc(
            details = listOf(
                "Shows package-level dependencies: which packages call into which other packages. Aggregates the call graph by package, excluding self-dependencies.",
                "Without {p:package=<regex>} all packages are shown. With {p:reverse=true} the direction flips to who depends on each package.",
            ),
            whenToUse = listOf("What depends on package X?", "What does package X import?", "What are the dependencies between packages?"),
            schema = listOf(
                """[{"package": "com.example.services", "dependencies": ["com.example.domain"]}]""",
                """With {p:reverse=true}: "dependents" replaces "dependencies".""",
            ),
            caveats = listOf("Bytecode analysis, so more accurate than source-level import scanning."),
            related = listOf("dsm", "why-depends", "cycles"),
        ),

        "why-depends" to TaskDoc(
            details = listOf(
                "Shows why one package depends on another at class level: each class-level edge from the source package to the target package, sorted by reference count.",
            ),
            whenToUse = listOf("Why does package A depend on package B?", "Which classes cause the cycle between A and B?"),
            schema = listOf("""[{"sourceClass": "com.example.api.Controller", "targetClass": "com.example.db.Repo", "count": 3}]"""),
            related = listOf("package-deps", "cycles", "rings"),
        ),

        "dsm" to TaskDoc(
            details = listOf(
                "Dependency Structure Matrix: a compact grid of inter-package dependencies. Row depends on column; cells show reference counts. Highlights cyclic dependencies with class-level detail.",
                "External dependencies (JDK, libraries) are filtered out unless {p:include-external=true}.",
            ),
            whenToUse = listOf("Give me an overview of the package structure", "Which packages are tangled?"),
            interpretation = listOf(
                "Cells below the diagonal are forward dependencies (expected); cells above it are backward dependencies and potential cycles. A clean DSM is lower-triangular.",
                "A dense row means the package depends on too many others: a decomposition or layering candidate. A dense column means high fan-in, fine for a stable abstraction and a smell for an implementation detail.",
                "Symmetric pairs (A→B and B→A) are mutual dependencies; the matrix prints a WARNING block with the class-level edges.",
                "Use {p:dsm-depth=2} to collapse deep packages and see higher-level structure.",
            ),
            schema = listOf(
                """{"packages": ["com.example.api", "com.example.db"], "cells": [{"from": "com.example.api", "to": "com.example.db", "count": 3}],""",
                """ "cycles": [{"packageA": "...", "packageB": "...", "forwardRefs": 3, "backwardRefs": 1, "forwardEdges": [...], "backwardEdges": [...]}]}""",
            ),
            related = listOf("package-deps", "cycles", "why-depends"),
        ),

        "cycles" to TaskDoc(
            details = listOf(
                "Detects dependency cycles with Tarjan's strongly-connected-components algorithm. Unlike {t:dsm} {p:cycles=true}, which only finds pairwise A<->B edges, this finds true multi-node cycles (A->B->C->A) and shows class-level edges for each.",
            ),
            whenToUse = listOf("Are there circular package dependencies?", "Gate the build on new cycles"),
            interpretation = listOf(
                "A 2-package cycle is often fixable by inverting one dependency or extracting a shared interface. A cycle of 3+ packages is an architectural tangle: find the weakest edge (fewest references) and break there first.",
                "Cycles in leaf packages (utilities, helpers) are low risk. Cycles spanning architectural layers (api↔persistence, domain↔infra) are high risk.",
                "Each cycle reports how many of its edges touch test sources; a cycle that only exists through test infrastructure is usually a {p:scope=prod} away from disappearing.",
            ),
            schema = listOf(
                """[{"packages": ["api", "service"],""",
                """  "edges": [{"from": "api", "to": "service", "classEdges": [{"source": "api.Controller", "target": "service.Service"}]}],""",
                """  "testInvolvement": {"testInvolved": 1, "total": 2}}]""",
            ),
            caveats = listOf(
                "CI gate: {u:cycles} {p:scope=prod} {pf:fail-on-violation} {p:max-cycles=0}. Raise max-cycles to allow a known baseline and ratchet it down.",
                "{p:plan-file=plan.json} simulates moves before you make them.",
            ),
            related = listOf("why-depends", "dsm", "simulate-move", "converge"),
        ),

        "simulate-move" to TaskDoc(
            details = listOf(
                "Predicts the cycle impact of moving a class to a different package without modifying code. Shows cycles removed and added by the simulated move.",
            ),
            whenToUse = listOf("If I move class X to package Y, what happens to the cycles?"),
            schema = listOf("""{"type": "com.example.api.Dto", "toPackage": "com.example.domain", "cyclesBefore": 2, "cyclesAfter": 1, "removed": [...], "added": [...]}"""),
            caveats = listOf("For several moves at once, put them in a plan file and run {t:cycles} {p:plan-file=plan.json} instead."),
            related = listOf("cycles", "move-suggest", "execute-plan"),
        ),

        "find-usages" to TaskDoc(
            details = listOf(
                "Finds project references to a type, method or field: method calls, field accesses and type references in compiled code. Owner-aware, so it distinguishes same-named methods on different types, and finds Kotlin property accessors by their compiled name (getMonthNumber for .monthNumber).",
                "Provide {p:type=X} for a comprehensive search, or {p:owner-class=X} with {p:method=m} or {p:field=f}. {p:field=f} alone searches every project class as owner, which is how you find a Kotlin top-level property whose file you do not know.",
                "Output is collapsed by default: new/init/checkcast become one 'instantiation', lambda callers roll up to the enclosing class. When the target is an interface, [impl] lines list implementors; {pf:include-impls} also searches usages of each implementor.",
            ),
            whenToUse = listOf("Where is type X used?", "Where is field X read or written?", "Is anything still referencing X before I delete it?", "Where do we call external API X.m? (migration)"),
            interpretation = listOf(
                "Same-package references, extension functions, companion objects and lambdas are all found; this is the main reason to prefer it over grep.",
                "{p:group-by=file} gives a one-line-per-file summary; {pf:raw} shows bytecode-level detail without collapsing.",
            ),
            schema = listOf(
                """[{"callerClass": "com.example.MyService", "callerMethod": "process",""",
                """  "targetOwner": "kotlinx.datetime.LocalDate", "targetMethod": "getMonthNumber", "kind": "method_call", "sourceFile": "MyService.kt"}]""",
                """With {p:group-by=file}: [{"sourceFile": "MyService.kt", "referenceCount": 3}]""",
            ),
            caveats = listOf("'method' and 'field' are mutually exclusive. Needs compiled bytecode, so it cannot find references to a class you have already deleted; grep covers that one case."),
            related = listOf("find-callers", "safe-delete", "context"),
        ),

        "rank" to TaskDoc(
            details = listOf(
                "Ranks types by structural importance using PageRank on the call graph. Types called by many important types score higher. Finds core abstractions, god classes and critical dependencies.",
            ),
            whenToUse = listOf("What are the most important types?", "Where does a change have the largest blast radius?"),
            interpretation = listOf(
                "High rank means many transitive dependents. A top-ranked concrete class is a candidate for interface extraction; a top-ranked interface or abstract class is healthy.",
                "Compare with fan-in from {t:complexity}: rank captures transitive importance, fan-in direct dependents. Moderate fan-in with high rank is a hidden linchpin.",
            ),
            schema = listOf("""[{"className": "com.example.Core", "rank": 0.42, "inDegree": 5, "outDegree": 2}]"""),
            related = listOf("complexity", "changed-since"),
        ),

        "dead" to TaskDoc(
            details = listOf(
                "Finds potential dead code: classes with no incoming type-level references and methods with no cross-class callers.",
                "Kotlin noise (data class boilerplate, companion objects, coroutine inner classes, bridge methods, static initialisers) is filtered automatically. Results are 'potential': reflection-based usage cannot be seen statically.",
                "All framework presets are active by default. Entry-point annotations (@GetMapping, @Scheduled) exclude a member from results; modifier annotations (@Transactional, @Cacheable) report it with LOW confidence; supertype entry points (JpaRepository) and receiver-type entry points (Ktor Route extension functions) are excluded.",
            ),
            whenToUse = listOf("Is there dead code?", "What became unused after removing feature X?"),
            interpretation = listOf(
                "Confidence HIGH = unreferenced in production and tests. MEDIUM = unreferenced in production but used by tests. LOW = carries framework annotations, likely invoked by reflection.",
                "Reason NO_REFERENCES = nothing points at it. TEST_ONLY = referenced only from test code.",
                "{p:scope=prod} hides TEST_ONLY items; {p:scope=test} shows only them (dead test infrastructure).",
                "{p:treat-as-dead=<preset>} drops a framework's protection; {p:treat-as-dead=ALL} drops all of them. Presets: ${FrameworkPresets.availablePresets().sorted().joinToString(", ")}. 'quarkus' bundles jaxrs, cdi, microprofile, jpa, jakarta, validation and jackson.",
                "Save {p:format=json} output as a baseline and pass it back with {p:baseline=dead-baseline.json} to see removed/remaining/new items between runs.",
            ),
            schema = listOf(
                """[{"className": "com.example.Orphan", "kind": "class", "sourceFile": "Orphan.kt", "confidence": "high", "reason": "NO_REFERENCES"},""",
                """ {"className": "com.example.Service", "memberName": "unused", "kind": "method", "sourceFile": "Service.kt", "confidence": "medium", "reason": "TEST_ONLY"}]""",
            ),
            caveats = listOf("Use {p:exclude=<regex>} for generated packages and {p:exclude-annotated=<a>,<b>} for entry points the presets do not know."),
            related = listOf("safe-delete", "find-usages"),
        ),

        "find-string-constant" to TaskDoc(
            details = listOf(
                "Searches string constants in compiled code matching a regex (case-insensitive). Finds URLs, SQL fragments, configuration keys, log messages and other literals. This pattern is plain regex, no camelCase expansion.",
            ),
            whenToUse = listOf("Where is string X hardcoded?", "Which classes contain SQL or URLs?"),
            schema = listOf("""[{"className": "com.example.Service", "methodName": "connect", "value": "http://localhost:8080", "sourceFile": "Service.kt"}]"""),
            related = listOf("annotations"),
        ),

        "annotations" to TaskDoc(
            details = listOf(
                "Finds classes, methods and fields annotated with names matching a regex. Scans all three targets by default; {p:target=class,method,field} narrows it.",
            ),
            whenToUse = listOf("Which classes use annotation X?", "Which methods have annotation X?", "Where do we use framework DI annotations?"),
            schema = listOf(
                """[{"className": "com.example.MyController", "sourceFile": "MyController.kt", "classAnnotations": ["RestController"],""",
                """  "methods": [{"method": "getUsers", "annotations": ["GetMapping"]}]}]""",
            ),
            related = listOf("find-usages", "dead"),
        ),

        "complexity" to TaskDoc(
            details = listOf(
                "Fan-in/fan-out coupling per class. Fan-out counts calls from this class to other classes; fan-in counts calls into it. Without a pattern, all project classes sorted by fan-out descending.",
            ),
            whenToUse = listOf("Which classes are most coupled?", "Is class X a god object?"),
            interpretation = listOf(
                "High fan-in (>20 callers) on a concrete class suggests a god object or central service; on an interface, abstract class or repository it is normal.",
                "High fan-out (>15 distinct dependencies) means the class depends on too many things and is a decomposition candidate; in a formatter or controller it is often acceptable coordination.",
            ),
            schema = listOf(
                """[{"className": "com.example.Service", "sourceFile": "Service.kt", "fanOut": 12, "fanIn": 5, "distinctOutgoingClasses": 4, "distinctIncomingClasses": 3,""",
                """  "outgoingByClass": [{"className": "com.example.Repo", "callCount": 5}], "incomingByClass": [{"className": "com.example.Controller", "callCount": 3}]}]""",
            ),
            related = listOf("rank", "class-metrics", "hotspots"),
        ),

        "class-metrics" to TaskDoc(
            details = listOf(
                "Per-class cohesion (TCC/LCC) and complexity (WMC/CBO/DIT). TCC/LCC is the fraction of method pairs sharing field access; low values mean the class does unrelated things (MONOLITH verdict). WMC is summed cyclomatic complexity, CBO counts distinct non-JDK types in signatures, DIT is superclass depth.",
            ),
            whenToUse = listOf("Which classes should be split?", "How cohesive is class X?"),
            interpretation = listOf("A MONOLITH verdict with high WMC is the strongest split candidate. Filter noise with {p:min-methods=3}."),
            schema = listOf("""[{"className": "com.example.Service", "package": "com.example", "totalMethods": 12, "tcc": 0.21, "lcc": 0.35, "verdict": "MONOLITH", "wmc": 48, "cbo": 9, "dit": 1}]"""),
            related = listOf("complexity", "cohesion"),
        ),

        "metrics" to TaskDoc(
            details = listOf(
                "Quick project health snapshot combining structural analysis and git history: total classes, packages, average fan-in/fan-out, cycle count, dead classes/methods and top hotspots.",
            ),
            whenToUse = listOf("How healthy is this codebase?", "Did my refactoring improve the structure?"),
            interpretation = listOf(
                "Use it as a baseline before and after refactoring. A rising cycle count or average fan-out over time means the architecture is degrading; a falling cycle count means layering work is taking effect.",
            ),
            schema = listOf(
                """{"totalClasses": 42, "packageCount": 8, "averageFanIn": 3.2, "averageFanOut": 5.1, "cycleCount": 1, "deadClassCount": 3, "deadMethodCount": 7,""",
                """ "topHotspots": [{"file": "Service.kt", "revisions": 25, "totalChurn": 340}]}""",
            ),
            related = listOf("report", "cycles", "dead", "hotspots"),
        ),

        "changed-since" to TaskDoc(
            details = listOf(
                "Blast radius of changes since a git ref: finds changed source files, maps them to compiled classes and shows their callers. Needs both git and compilation.",
            ),
            whenToUse = listOf("What is the blast radius of my changes?", "What is affected by the commits since main?"),
            schema = listOf(
                """{"changedClasses": [{"className": "com.example.Service", "sourceFile": "Service.kt", "callers": [{"className": "com.example.Controller", "method": "handle"}]}],""",
                """ "unresolvedFiles": ["build.gradle.kts"]}""",
            ),
            caveats = listOf("Non-class files (build scripts, resources) appear under unresolvedFiles. Follow up with {t:find-callers} to trace deeper."),
            related = listOf("find-callers", "hotspots"),
        ),

        "context" to TaskDoc(
            details = listOf(
                "Everything about a class in one call: class detail, callers tree, callees tree, interface implementors and implemented interfaces.",
            ),
            whenToUse = listOf("Give me everything about class X", "I need the full picture before touching X"),
            schema = listOf(
                """{"classDetail": {"className": "...", "sourceFile": "...", ...}, "callers": [{"method": "...", "children": [...]}],""",
                """ "callees": [{"method": "...", "children": [...]}], "implementors": [{"className": "...", "sourceFile": "..."}], "implementedInterfaces": ["..."]}""",
            ),
            related = listOf("class-detail", "find-callers", "find-callees"),
        ),

        "distance" to TaskDoc(
            details = listOf(
                "Structural distance between coupled packages: tree hops from source to target package in the package hierarchy. High distance with many dependencies is long-range coupling.",
            ),
            whenToUse = listOf("How far apart are coupled packages?"),
            interpretation = listOf(
                "distance=1 is siblings or parent/child (close, expected). 2-3 is nearby subtrees, normal for api→service or service→persistence. 4+ is very different parts of the tree; review whether an interface or shared module should mediate.",
                "High distance plus high dependency count is the priority case for an abstraction boundary or co-location.",
            ),
            schema = listOf("""[{"source": "com.example.api", "target": "com.example.persistence", "distance": 2, "deps": 5}]"""),
            related = listOf("strength", "balance"),
        ),

        "strength" to TaskDoc(
            details = listOf(
                "Classifies each inter-package dependency edge by integration strength, the kind of knowledge shared (Balanced Coupling, Vlad Khononov): CONTRACT (interface or abstract class), MODEL (data class or record), FUNCTIONAL (concrete class). When a pair has edges at several levels the strongest wins.",
            ),
            whenToUse = listOf("How tightly coupled are packages?", "Do dependencies go through contracts or concrete classes?"),
            interpretation = listOf(
                "CONTRACT is healthy coupling through abstractions. FUNCTIONAL between distant packages is the priority refactoring target: introduce an interface or shared model.",
                "Combine with {t:distance}: high distance plus FUNCTIONAL is the worst case, low distance plus CONTRACT the best.",
            ),
            schema = listOf("""[{"source": "com.example.api", "target": "com.example.model", "strength": "MODEL", "contract": 1, "model": 2, "functional": 0, "totalDeps": 3}]"""),
            related = listOf("distance", "balance", "rings"),
        ),

        "cohesion" to TaskDoc(
            details = listOf(
                "Scores package cohesion: internal class dependencies divided by total (internal + outgoing). Low cohesion means a package whose classes depend more on other packages than on each other. Verdict per package: COHESIVE, REVIEW or THIN_LAYER.",
            ),
            whenToUse = listOf("Which packages should be split?", "Is package X a real module or a dumping ground?"),
            interpretation = listOf(
                "Domain packages should be COHESIVE; port packages that hold only interfaces are legitimately THIN_LAYER. REVIEW is the split candidate. {p:min-edges=10} filters noise from tiny packages.",
            ),
            schema = listOf("""[{"package": "com.example.services", "classes": 7, "internalEdges": 12, "externalEdges": 30, "cohesion": 0.29, "verdict": "REVIEW"}]"""),
            related = listOf("class-metrics", "move-suggest"),
        ),

        "move-suggest" to TaskDoc(
            details = listOf(
                "Suggests misplaced classes from dependency gravity: a class with more outgoing edges to another package than to its own. Ubiquitous types (high fan-in), library packages, and parent/child packages are never proposed as destinations.",
            ),
            whenToUse = listOf("Which classes are in the wrong package?"),
            interpretation = listOf(
                "A row marked absence-driven has no same-package edges to divide by, so its confidence rounds toward 1.00 without saying much; treat it as advisory.",
                "Not for tests: a test's gravity is dominated by object mothers and fixtures. Use {t:test-alignment} for test placement.",
            ),
            schema = listOf("""[{"class": "com.example.api.Dto", "currentPackage": "com.example.api", "suggestedPackage": "com.example.domain", "edgesToCurrent": 1, "edgesToSuggested": 6, "confidence": 0.86, "absenceDriven": false}]"""),
            caveats = listOf("{p:plan-file=plan.json} shows suggestions as if the planned moves were already made."),
            related = listOf("suggest-structure", "simulate-move", "move-class"),
        ),

        "suggest-structure" to TaskDoc(
            details = listOf(
                "Groups move-suggest results by target package to show structural drift: which packages are quietly absorbing classes from where.",
            ),
            whenToUse = listOf("Is a whole group of classes drifting toward another package?"),
            schema = listOf("""[{"targetPackage": "com.example.domain", "classes": [{"class": "com.example.api.Dto", "currentPackage": "com.example.api", "confidence": 0.86}]}]"""),
            related = listOf("move-suggest", "move-package"),
        ),

        "balance" to TaskDoc(
            details = listOf(
                "Composite balanced-coupling verdict per package pair, combining {t:strength}, ring separation from {t:rings}, and {t:volatility}. Needs compiled code and git history.",
            ),
            whenToUse = listOf("Overall coupling health?", "Which package pairs are problematic?"),
            interpretation = listOf(
                "DANGER = FUNCTIONAL coupling that crosses rings with high volatility. TOLERABLE = poor modularity but low volatility; monitor. BALANCED = strength and distance are complementary, or intentional layering (MODEL/CONTRACT at short distance).",
                "Distance here is architectural rings crossed, not package-name nesting; co-located features in the same ring score 0. Composition roots are never DANGER because their fan-out is by design.",
                "Start here, then drill into {t:strength}, {t:distance} and {t:volatility} for the individual dimensions.",
            ),
            schema = listOf("""[{"source": "com.example.api", "target": "com.example.persistence", "strength": "FUNCTIONAL", "distance": 3, "sourceVolatility": 12, "targetVolatility": 8, "verdict": "DANGER", "suggestion": "..."}]"""),
            related = listOf("strength", "distance", "volatility", "rings"),
        ),

        "rings" to TaskDoc(
            details = listOf(
                "Detects hexagonal architecture rings from dependency inversions. A ring boundary is a port: an interface owned by the inside, implemented by a class that does I/O. The ring count is emergent (0 = innermost); code with no inversion reports 1 ring and says why.",
                "Reports OUTWARD violations only (an inner class reaching outward across a boundary); same-ring edges are peers and composition roots are excluded. Two further checks are independent of ring number: a domain class must never depend on service-tier code, and calling a concrete adapter instead of its port is a port bypass. A class and its own Kt file facade are never a violation.",
                "Optional cnav-config.json \"rings\" section: expected, compositionRoots, notCompositionRoots, adapters, notAdapters, valuePackages, frameworkPackages, serviceTier, notServiceTier. A directive matching no class is reported, not ignored. {pf:bootstrap-config} generates a starting section from detected patterns.",
            ),
            whenToUse = listOf("Does my code follow layer rules?", "Are there hexagonal architecture violations?", "Gate the build on layering regressions"),
            interpretation = listOf(
                "An OUTWARD violation means an inner class depends on an outer one: extract an interface (port) in the inner ring and move the concrete class outward. Run {t:why-depends} on the pair to see the exact edges.",
                "\"1 ring, no inversion boundaries\" means nothing inverts a dependency yet: find a class that does I/O, extract the interface its callers need, and have the I/O class implement it.",
                "The \"Why these are adapters\" histogram shows the classification reason per adapter with the evidence type; a surprising adapter usually names the external type that should go in valuePackages or frameworkPackages.",
                "Kt and Test suffixes are stripped automatically; write *Route, not *RouteKt.",
            ),
            schema = listOf(
                """{"ringCount": 3, "rings": [{"ring": 0, "classes": ["..."]}], "violations": [{"from": "com.example.domain.Order", "to": "com.example.db.OrderDao", "fromRing": 0, "toRing": 2, "kind": "OUTWARD"}],""",
                """ "adapters": [{"className": "...", "reason": "FRAMEWORK_TYPE", "evidence": "..."}], "compositionRoots": ["..."]}""",
            ),
            caveats = listOf("CI gate: {u:rings} {p:scope=prod} {pf:fail-on-violation} {p:max-violations=0}."),
            related = listOf("why-depends", "strength", "balance", "ambient", "test-coupling"),
        ),

        "type-affinity" to TaskDoc(
            details = listOf(
                "Which packages have strong affinity to the types in a target package. Separates single-owner types (candidates for moving to their only consumer) from shared types.",
            ),
            whenToUse = listOf("Who really owns the types in package X?", "Which types in a shared package belong to one consumer?"),
            schema = listOf("""{"singleOwner": [{"type": "com.example.shared.Money", "owner": "com.example.billing", "usages": 9, "ringImpact": "..."}], "shared": [{"type": "...", "consumers": ["..."]}]}"""),
            related = listOf("move-suggest", "cohesion"),
        ),

        "size" to TaskDoc(
            details = listOf("Lists source files (Kotlin/Java) by line count, largest first. {p:over=100} hides small files."),
            whenToUse = listOf("Which source files are the largest?"),
            schema = listOf("""[{"file": "services/UserService.kt", "lines": 61}, {"file": "domain/Domain.kt", "lines": 22}]"""),
            related = listOf("complexity", "hotspots"),
        ),

        "duplicates" to TaskDoc(
            details = listOf(
                "Token-based duplicate code detection across source files. Package and import declarations are dropped before tokenising so import blocks do not count. {p:min-tokens=100} keeps only large blocks.",
            ),
            whenToUse = listOf("Is there duplicated code?", "Find copy-paste blocks"),
            schema = listOf("""[{"tokenCount": 149, "locations": [{"file": "Repository.kt", "startLine": 258, "endLine": 293}, {"file": "RepositoryFake.kt", "startLine": 90, "endLine": 119}]}]"""),
            related = listOf("size"),
        ),

        "test-coupling" to TaskDoc(
            details = listOf(
                "Detects callers that bypass domain services by calling port interface methods directly (Testing Through The Domain violations).",
                "{p:subject=tests} (default): tests should mutate state through domain or service methods, not by calling repository or client methods. {p:subject=adapters}: a driving adapter (route, controller) calling a port WRITE method directly means the use-case orchestration lives in the adapter, which {t:rings} cannot see because it only measures direction. Read-side pass-through from an adapter is not flagged.",
            ),
            whenToUse = listOf("Are tests bypassing the domain?", "TTTD violations?", "Do controllers write to repositories directly?"),
            interpretation = listOf(
                "Each finding shows the test class, the port it calls, a confidence score, and whether it is an adapter test (testing the adapter itself, which is acceptable).",
                "A high-confidence violation is a service test calling repository.save() to set up state; fix by calling the service method that production uses. Violations often mean a fake is missing for that port.",
                "Acceptable exceptions: adapter tests, explicitly marked integration tests, and test data builders that use ports internally. Exclude them with {p:exclude=\".*IntegrationTest\"}.",
            ),
            schema = listOf("""[{"caller": "com.example.UserServiceTest", "port": "com.example.UserRepository", "methods": ["save"], "confidence": 0.9, "adapterTest": false}]"""),
            caveats = listOf("Set {p:ports=\".*Repository|.*Client\"} to name your port types. CI gate with {pf:fail-on-violation} {p:max-violations=0}."),
            related = listOf("find-interfaces", "rings", "test-alignment"),
        ),

        "test-alignment" to TaskDoc(
            details = listOf(
                "Checks that every test sits with, and is named after, the class it tests. The subject is inferred from the call graph (the production class the test drives through the most distinct methods, which sees same-package calls that imports cannot) and cross-checked against the FooTest -> Foo convention.",
                "Three finding kinds, reported separately because they have different fixes: package (both signals agree the subject is elsewhere → {t:move-class}), name (named after a class in another package but drives something else → {t:rename-class}), duplicate (two test classes sharing a simple name). Plus an advisory list of tests named after a class that does not exist.",
            ),
            whenToUse = listOf("Are my tests in the right place?", "Is this test named after what it tests?"),
            interpretation = listOf(
                "A package move is only proposed where both signals agree; where they disagree, the disagreement is the finding.",
                "Do not use {t:move-suggest} for this: a test's dependency gravity is dominated by object mothers and fixtures. {p:exclude=\"Journey|EndpointsTest\"} drops tests that legitimately span many classes.",
            ),
            schema = listOf(
                """{"analysedTests": 96, "violationCount": 3, "packageMismatches": [{"testClass": "...", "subject": "...", "testPackage": "...", "subjectPackage": "...", "distinctMethods": 5}],""",
                """ "nameMismatches": [{"testClass": "...", "nameSubject": "...", "callSubject": "..."}], "duplicateNames": [{"simpleName": "...", "testClasses": ["..."]}], "unknownSubjects": [...]}""",
            ),
            related = listOf("move-class", "test-coupling"),
        ),

        "ambient" to TaskDoc(
            details = listOf(
                "Detects domain (ring-0) classes reading ambient input directly: the wall clock (Instant.now(), System.currentTimeMillis()), randomness (UUID.randomUUID()), the environment (System.getenv()), the filesystem or the network. Invisible to {t:rings} and every other direction check because it is a static call on an already-imported type, not a dependency edge.",
                "Passing an injected Clock (now(clock)) is not flagged; a zone overload (now(APP_ZONE)) still is.",
            ),
            whenToUse = listOf("Does domain code read the clock, randomness or environment directly?", "Why are my domain tests non-deterministic?"),
            interpretation = listOf("The clockConvention line says whether the codebase already injects a Clock somewhere; if so, the fix is to thread that same Clock into the flagged classes."),
            schema = listOf("""{"domainClassCount": 40, "clockConvention": "...", "findings": [{"category": "CLOCK", "class": "com.example.Order", "method": "place", "call": "java.time.Instant.now", "sourceFile": "Order.kt", "line": 42}]}"""),
            related = listOf("rings", "test-coupling"),
        ),

        "report" to TaskDoc(
            details = listOf(
                "Consolidated codebase health report: metrics, cycles, rings, move suggestions, cohesion and dead code in a single pass, as one sectioned overview.",
            ),
            whenToUse = listOf("Give me a one-shot health report of the whole codebase"),
            schema = listOf("""{"metrics": {...}, "cycles": [...], "rings": {...}, "moveSuggestions": [...], "cohesion": [...], "deadCode": [...]}"""),
            caveats = listOf("Each section is the same JSON its standalone task produces."),
            related = listOf("metrics", "converge"),
        ),

        "converge" to TaskDoc(
            details = listOf(
                "Composite architectural signal. intersect mode (default) cross-references cycles, ring violations and change coupling and reports package pairs where the signals agree: ACT NOW (structural + coupled), LATENT (structural only), MISSING ABSTRACTION (coupled only). risk mode ranks classes by change frequency × complexity × coupling degree.",
                "Includes test sources by default. When the result is large it prints an advisory suggesting {p:scope=prod} or {p:exclude=<regex>}, since manually wired DI and shared test infrastructure often add cycles that do not exist in production.",
            ),
            whenToUse = listOf("Where should I start refactoring?", "Which structural problems also hurt in practice?"),
            interpretation = listOf(
                "ACT NOW pairs are where a structural problem is already costing change effort. LATENT is structure with no change pressure yet. MISSING ABSTRACTION is co-change without a dependency, which usually means a concept with no home.",
                "Honours the project's cnav-config.json rings settings, so violations you have already corrected in config do not reappear here.",
            ),
            schema = listOf(
                """{"mode": "intersect", "edges": [{"source": "com.example.api", "target": "com.example.db", "verdict": "ACT NOW", "hasCycle": true, "hasRingViolation": false, "couplingDegree": 0.7}], "advisory": "..."}""",
                """risk mode: {"mode": "risk", "entries": [{"className": "...", "sourceFile": "...", "changeFrequency": 25, "complexity": 14, "riskScore": 0.81}]}""",
            ),
            related = listOf("cycles", "rings", "coupling", "report"),
        ),

        // ---------------------------------------------------------------- git history

        "hotspots" to TaskDoc(
            details = listOf("Ranks files by change frequency. The most-changed files are where effort concentrates and are priority refactoring targets if they also have structural problems."),
            whenToUse = listOf("Which files change the most?"),
            interpretation = listOf(
                "High revision count plus structural problems (high fan-out, cycles) is the priority refactoring target. High revision count with clean structure is just active development.",
            ),
            schema = listOf("""[{"file": "src/main/kotlin/Service.kt", "revisions": 25, "totalChurn": 340}]"""),
            related = listOf("complexity", "churn", "authors", "converge"),
        ),

        "churn" to TaskDoc(
            details = listOf("Lines added and deleted per file over git history. Pre-release churn is a good predictor of post-release defects."),
            whenToUse = listOf("Which files are being rewritten repeatedly?"),
            interpretation = listOf(
                "Large added+deleted with low net change means repeated rewriting: look for design instability or unclear requirements. Churn concentrated in a few files marks volatility hotspots; cross-reference with {t:complexity}. High churn in tests is usually fine.",
            ),
            schema = listOf("""[{"file": "src/main/kotlin/Service.kt", "added": 400, "deleted": 350, "commits": 12}]"""),
            related = listOf("hotspots", "code-age"),
        ),

        "code-age" to TaskDoc(
            details = listOf("Months since each file was last changed. Stable old code is good; frequently changing old code with structural problems is a hotspot."),
            whenToUse = listOf("Which code is mature and which is still moving?"),
            interpretation = listOf(
                "Old, stable and clean: leave it alone. Old but recurring in hotspots: legacy burden, prioritise. Young code across many files: active development area, expect churn.",
            ),
            schema = listOf("""[{"file": "src/main/kotlin/Legacy.kt", "ageMonths": 18, "lastChangeDate": "2025-04-01"}]"""),
            related = listOf("hotspots", "churn"),
        ),

        "authors" to TaskDoc(
            details = listOf("Distinct contributors per file. More developers on a module means more coordination; high author counts correlate with defects."),
            whenToUse = listOf("Who owns this code?", "Which files have no clear owner?"),
            interpretation = listOf(
                "Many authors means higher coordination cost and defect risk; one or two means strong ownership. Combine with {t:hotspots} to find heavily changed files without an owner.",
            ),
            schema = listOf("""[{"file": "src/main/kotlin/Service.kt", "authors": 5, "revisions": 25}]"""),
            related = listOf("hotspots"),
        ),

        "coupling" to TaskDoc(
            details = listOf("Files that change together in the same commits: implicit dependencies invisible to call graphs. Complements structural {t:package-deps} with behavioural data."),
            whenToUse = listOf("Which files change together?", "Is there a hidden dependency between A and B?"),
            interpretation = listOf(
                "High coupling degree (>60%) with many shared revisions is a strong implicit dependency: extract the shared logic or make it explicit. High degree with few shared revisions (<5) may be coincidence; wait for data.",
                "A test file coupled with its production file is expected. Pairs marked stale have not changed together recently.",
            ),
            schema = listOf("""[{"entity": "Service.kt", "coupled": "Repository.kt", "degree": 71, "sharedRevs": 10, "avgRevs": 14, "stale": false}]"""),
            related = listOf("converge", "package-deps"),
        ),

        "volatility" to TaskDoc(
            details = listOf("Aggregates file-level git history to the package level: which packages change most. A key dimension of Balanced Coupling; volatile packages need loose coupling, stable ones tolerate tighter coupling."),
            whenToUse = listOf("Which packages change the most?"),
            schema = listOf("""[{"package": "com.example.api", "revisions": 120, "fileCount": 8, "avgRevisionsPerFile": 15.0, "totalChurn": 2400}]"""),
            related = listOf("balance", "hotspots"),
        ),

        // ---------------------------------------------------------------- mutations

        "rename-param" to TaskDoc(
            details = listOf("Renames a method parameter and updates all named-argument call sites. Applies by default; {pf:preview} shows the plan without writing."),
            whenToUse = listOf("Rename a method parameter?"),
            schema = listOf(
                """{"preview": false, "param": "limit", "newName": "maxResults",""",
                """ "changes": [{"filePath": "/src/main/kotlin/Service.kt", "diff": ["- limit: Int", "+ maxResults: Int"]}]}""",
            ),
            related = listOf("change-signature", "rename-method"),
        ),

        "rename-method" to TaskDoc(
            details = listOf("Renames a method and updates all call sites, including implementations and overrides of interface methods. Applies by default; {pf:preview} shows the plan without writing."),
            whenToUse = listOf("Rename a method?"),
            schema = listOf(
                """{"preview": false, "method": "find", "newName": "search",""",
                """ "changes": [{"filePath": "/src/main/kotlin/Service.kt", "diff": ["- fun find(", "+ fun search("]}]}""",
            ),
            related = listOf("find-callers", "change-signature"),
        ),

        "move-class" to TaskDoc(
            details = listOf(
                "Moves a class to a different package and/or renames it, updating the package declaration, the file location and every reference, including same-package references that have no import. {t:rename-class} is an alias; change the package part of 'to' to also move.",
                "Imports that become same-package after the move are stripped (aliased and nested-class imports are kept). TEXT and LLM output carry a 'file moved: old -> new' line; do not git mv afterwards.",
            ),
            whenToUse = listOf("Move a class to a different package?", "Rename a class?"),
            schema = listOf(
                """{"preview": false, "from": "com.example.services.UserService", "to": "com.example.domain.AccountService",""",
                """ "changes": [{"filePath": "/src/main/kotlin/Service.kt", "diff": ["- package com.example.services", "+ package com.example.domain"]}]}""",
            ),
            caveats = listOf("A file that declares several classes is refused with a message naming them; use {t:move-file} or {p:from-file=<path>}."),
            related = listOf("move-file", "move-package", "find-usages"),
        ),

        "move-file" to TaskDoc(
            details = listOf("Moves a Kotlin source file to a new package, updating all class and top-level declaration references. Handles multi-class files and Kt facade files."),
            whenToUse = listOf("Move a whole file (several classes, top-level functions) to another package?"),
            schema = listOf("""{"preview": false, "changes": [{"filePath": "...", "before": "...", "after": "..."}]}"""),
            related = listOf("move-class", "move-package"),
        ),

        "move-package" to TaskDoc(
            details = listOf("Moves all classes in a package to a different package, updating all references. Applies by default; {pf:preview} shows the plan without writing."),
            whenToUse = listOf("Move all classes in a package?", "Rename a package?"),
            schema = listOf("""{"preview": false, "results": [{"from": "...", "to": "...", "changes": [...]}]}"""),
            related = listOf("move-class", "suggest-structure"),
        ),

        "rename-property" to TaskDoc(
            details = listOf("Renames a val/var property and updates all access sites, constructor named arguments and copy() calls. Applies by default; {pf:preview} shows the plan without writing."),
            whenToUse = listOf("Rename a property?"),
            schema = listOf(
                """{"preview": false, "property": "fullName", "newName": "displayName",""",
                """ "changes": [{"filePath": "/src/main/kotlin/UserProfile.kt", "diff": ["- val fullName: String", "+ val displayName: String"]}]}""",
            ),
            related = listOf("find-usages", "rename-method"),
        ),

        "change-signature" to TaskDoc(
            details = listOf(
                "Changes a method signature: add, remove or reorder parameters. Rewrites the declaration and all call sites; positional arguments are reordered, named arguments preserved. New parameters need defaults for existing call sites.",
            ),
            whenToUse = listOf("Change a method's parameters?", "Add, remove or reorder parameters?"),
            schema = listOf("""{"preview": false, "changes": [{"filePath": "...", "before": "...", "after": "..."}]}"""),
            related = listOf("rename-param", "rename-method"),
        ),

        "safe-delete" to TaskDoc(
            details = listOf("Deletes a class, or one method of it, only if bytecode analysis finds no usages. Omit the method to delete the whole class."),
            whenToUse = listOf("Delete X if nothing uses it"),
            interpretation = listOf("A refusal lists the usages found; those are the call sites to migrate first. Afterwards grep for stale string references, which bytecode cannot see."),
            schema = listOf("""{"deleted": true, "changes": [{"filePath": "...", "before": "...", "after": "..."}], "reason": "...", "usages": [{"callerClass": "...", "callerMethod": "...", "sourceFile": "..."}]}"""),
            related = listOf("dead", "find-usages"),
        ),

        "execute-plan" to TaskDoc(
            details = listOf(
                "Executes a refactoring plan file, applying each move step in order. The plan is a JSON array of actions; class names are resolved to FQCNs up front from the compiled class index before any step runs.",
                "The same plan file on analysis tasks ({t:cycles}, {t:dsm}, {t:rings}, {t:metrics}, {t:balance}, {t:move-suggest}) is a what-if simulation; here it makes real changes.",
            ),
            whenToUse = listOf("Apply a sequence of planned moves at once"),
            schema = listOf("""{"results": [{"step": {"action": "move", "type": "...", "to": "..."}, "changes": [...], "error": null}], "totalChanges": 12}"""),
            caveats = listOf("A step that fails carries an error; a run that only reports 'No changes needed' with errors present is a bug, report it."),
            related = listOf("cycles", "move-class", "move-suggest"),
        ),
    )
}
