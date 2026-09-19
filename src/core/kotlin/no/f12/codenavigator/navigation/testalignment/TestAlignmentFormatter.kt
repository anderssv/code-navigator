package no.f12.codenavigator.navigation.testalignment

import no.f12.codenavigator.formatting.JsonRaw
import no.f12.codenavigator.formatting.jsonArray
import no.f12.codenavigator.formatting.jsonObject

object TestAlignmentFormatter {

    const val NO_FINDINGS = "Every test sits with, and is named after, the class it tests."

    fun formatText(result: TestAlignmentResult): String {
        if (result.isEmpty()) return "$NO_FINDINGS (${result.analysedTests} test class(es) checked)"

        return buildString {
            appendLine(header(result))

            if (result.packageMismatches.isNotEmpty()) {
                appendLine()
                appendLine("Wrong package (${result.packageMismatches.size}) — name and call graph agree on the subject; fix with cnavMoveClass:")
                result.packageMismatches.forEach {
                    appendLine(
                        "  ${it.testClass.simpleName()}  ${it.testPackage} -> ${it.subjectPackage}" +
                            "  (subject ${it.subject.simpleName()}, ${it.distinctMethods} distinct method(s) called)",
                    )
                }
            }

            if (result.nameMismatches.isNotEmpty()) {
                appendLine()
                appendLine("Wrong name (${result.nameMismatches.size}) — named after a class in another package but driving something else; fix with cnavRenameClass, or move it to where its name points:")
                result.nameMismatches.forEach {
                    appendLine(
                        "  ${it.testClass.simpleName()}  names ${it.nameSubject.value}" +
                            " but mostly calls ${it.callSubject.className.value} (${it.callSubject.distinctMethods} distinct method(s))",
                    )
                }
            }

            if (result.duplicateNames.isNotEmpty()) {
                appendLine()
                appendLine("Duplicate test class names (${result.duplicateNames.size}) — test reports cannot tell these apart:")
                result.duplicateNames.forEach {
                    appendLine("  ${it.simpleName}  ${it.testClasses.joinToString(", ") { cls -> cls.packageName().toString() }}")
                }
            }

            if (result.unknownSubjects.isNotEmpty()) {
                appendLine()
                appendLine("Named after nothing that exists (${result.unknownSubjects.size}) — advisory, not counted as violations:")
                appendLine("  A behaviour-named test is a legitimate convention; a misnamed one looks the same from here.")
                result.unknownSubjects.forEach {
                    appendLine(
                        "  ${it.testClass.simpleName()}  no production class named ${it.expectedSubjectName};" +
                            " it mostly calls ${it.callSubject.className.value} (${it.callSubject.distinctMethods} distinct method(s))",
                    )
                }
            }
        }.trimEnd()
    }

    fun formatLlm(result: TestAlignmentResult): String {
        if (result.isEmpty()) return "$NO_FINDINGS (${result.analysedTests} test class(es) checked)"

        return buildString {
            appendLine(header(result))
            result.packageMismatches.forEach {
                appendLine("package ${it.testClass.value} ${it.testPackage} -> ${it.subjectPackage} subject=${it.subject.value} methods=${it.distinctMethods}")
            }
            result.nameMismatches.forEach {
                appendLine("name ${it.testClass.value} names=${it.nameSubject.value} calls=${it.callSubject.className.value} methods=${it.callSubject.distinctMethods}")
            }
            result.duplicateNames.forEach {
                appendLine("duplicate ${it.simpleName} ${it.testClasses.joinToString(",") { cls -> cls.value }}")
            }
            result.unknownSubjects.forEach {
                appendLine("advisory-unknown-subject ${it.testClass.value} expected=${it.expectedSubjectName} calls=${it.callSubject.className.value} methods=${it.callSubject.distinctMethods}")
            }
        }.trimEnd()
    }

    fun formatJson(result: TestAlignmentResult): String =
        jsonObject(
            "analysedTests" to result.analysedTests,
            "excludedTests" to result.excludedTests,
            "violationCount" to result.violationCount,
            "findingCount" to result.findingCount,
            "packageMismatches" to JsonRaw(
                jsonArray(result.packageMismatches) {
                    jsonObject(
                        "testClass" to it.testClass.value,
                        "subject" to it.subject.value,
                        "testPackage" to it.testPackage.value,
                        "subjectPackage" to it.subjectPackage.value,
                        "distinctMethods" to it.distinctMethods,
                    )
                },
            ),
            "nameMismatches" to JsonRaw(
                jsonArray(result.nameMismatches) {
                    jsonObject(
                        "testClass" to it.testClass.value,
                        "nameSubject" to it.nameSubject.value,
                        "callSubject" to it.callSubject.className.value,
                        "distinctMethods" to it.callSubject.distinctMethods,
                    )
                },
            ),
            "unknownSubjects" to JsonRaw(
                jsonArray(result.unknownSubjects) {
                    jsonObject(
                        "testClass" to it.testClass.value,
                        "expectedSubjectName" to it.expectedSubjectName,
                        "callSubject" to it.callSubject.className.value,
                        "distinctMethods" to it.callSubject.distinctMethods,
                        "advisory" to true,
                    )
                },
            ),
            "duplicateNames" to JsonRaw(
                jsonArray(result.duplicateNames) {
                    jsonObject(
                        "simpleName" to it.simpleName,
                        "testClasses" to JsonRaw(jsonArray(it.testClasses) { cls -> "\"${cls.value}\"" }),
                    )
                },
            ),
        )

    private fun header(result: TestAlignmentResult): String {
        val excluded = if (result.excludedTests > 0) ", ${result.excludedTests} excluded" else ""
        val advisory = if (result.unknownSubjects.isNotEmpty()) " + ${result.unknownSubjects.size} advisory" else ""
        return "test-alignment: ${result.violationCount} violation(s)$advisory in ${result.analysedTests} test class(es)$excluded"
    }
}
