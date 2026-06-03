package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

data class ClassDependencies(
    val projectDeps: Set<ClassName>,
    val externalDeps: Set<ClassName>,
)

object ClassRingClassifier {

    private val FRAMEWORK_PACKAGES = setOf(
        "io.ktor", "org.springframework", "jakarta.", "javax.",
        "org.jetbrains.exposed", "org.hibernate",
        "io.quarkus", "io.vertx",
        "org.apache.http", "okhttp3", "java.net.http",
        "java.sql", "javax.sql",
        "com.zaxxer.hikari",
        "org.eclipse.microprofile",
        "io.grpc", "net.devh.boot.grpc",
    )

    fun classify(classDeps: Map<ClassName, ClassDependencies>): Map<ClassName, Int> {
        // Step 1: Determine which classes are adapters (have framework imports)
        val isAdapter = classDeps.mapValues { (_, deps) ->
            deps.externalDeps.any { ext ->
                FRAMEWORK_PACKAGES.any { prefix -> ext.value.startsWith(prefix) }
            }
        }

        // Step 2: Find SCCs among project classes
        val dependsOn = classDeps.mapValues { (_, deps) ->
            deps.projectDeps.filter { it in classDeps }.toSet()
        }
        val sccs = TarjanSCC.findSCCs(classDeps.keys, dependsOn).toList()

        // Step 3: Build SCC DAG and assign rings
        val sccOf = mutableMapOf<ClassName, Int>()
        sccs.forEachIndexed { idx, scc -> scc.forEach { sccOf[it] = idx } }

        val sccDeps = sccs.indices.associateWith { idx ->
            sccs[idx].flatMap { dependsOn[it] ?: emptySet() }
                .map { sccOf[it]!! }
                .filter { it != idx }
                .toSet()
        }

        val sccIsAdapter = sccs.indices.associateWith { idx ->
            sccs[idx].any { isAdapter[it] == true }
        }

        // Longest path on the DAG
        val sccRings = mutableMapOf<Int, Int>()

        fun computeSccRing(sccIdx: Int): Int {
            sccRings[sccIdx]?.let { return it }
            val targets = sccDeps[sccIdx] ?: emptySet()
            val basedOnDeps = if (targets.isEmpty()) 0
            else targets.maxOf { computeSccRing(it) } + 1

            val ring = if (sccIsAdapter[sccIdx] == true) maxOf(basedOnDeps, 1)
            else basedOnDeps

            sccRings[sccIdx] = ring
            return ring
        }

        for (idx in sccs.indices) computeSccRing(idx)

        // Map back to classes
        return classDeps.keys.associateWith { cls -> sccRings[sccOf[cls]!!] ?: 0 }
    }
}
