package no.f12.codenavigator.navigation

import java.io.File

fun scanProjectClasses(classDirectories: List<File>): Set<ClassName> =
    classDirectories
        .filter { it.exists() }
        .flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .mapNotNull { classFile ->
                    runCatching {
                        ClassName.fromInternal(createClassReader(classFile).className).topLevelClass()
                    }.getOrNull()
                }
                .toList()
        }
        .toSet()
