package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.types.ClassName

object TestCouplingFormatter {

    fun formatText(result: TestCouplingResult): String {
        if (result.violations.isEmpty()) return "No TTTD violations found. All test classes use domain-oriented setup."

        val classSummaries = result.violations
            .groupBy { it.testClass }
            .map { (testClass, violations) ->
                val verdict = result.verdictFor(testClass)
                val shortName = testClass.value.substringAfterLast('.')
                "$shortName  verdict=$verdict  port-calls=${violations.size}"
            }

        return classSummaries.joinToString("\n")
    }

    fun formatLlm(result: TestCouplingResult): String {
        if (result.violations.isEmpty()) return "No TTTD violations found. All test classes use domain-oriented setup."

        return result.violations.joinToString("\n") { v ->
            val shortTestClass = v.testClass.value.substringAfterLast('.')
            val shortPort = v.portInterface.value.substringAfterLast('.')
            "$shortTestClass.${v.testMethod} -> ${shortPort}.${v.portMethod.methodName}"
        }
    }
}
