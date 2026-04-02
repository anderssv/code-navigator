package no.f12.codenavigator.navigation.core

import java.io.File

class SourceSetResolver private constructor(
    private val sourceSets: Map<ClassName, SourceSet>,
    val classDirectories: List<File>,
) {
    fun sourceSetOf(className: ClassName): SourceSet? = sourceSets[className]

    companion object {
        fun from(taggedDirectories: List<Pair<File, SourceSet>>): SourceSetResolver {
            val sourceSets = mutableMapOf<ClassName, SourceSet>()

            taggedDirectories
                .filter { it.first.exists() }
                .forEach { (dir, sourceSet) ->
                    dir.walkTopDown()
                        .filter { it.isFile && it.extension == "class" }
                        .forEach { classFile ->
                            val relativePath = classFile.relativeTo(dir).path
                            val className = ClassName(
                                relativePath
                                    .removeSuffix(".class")
                                    .replace(File.separatorChar, '.'),
                            )
                            sourceSets[className] = sourceSet
                        }
                }

            return SourceSetResolver(
                sourceSets = sourceSets,
                classDirectories = taggedDirectories.map { it.first },
            )
        }
    }
}
