package no.f12.codenavigator.navigation.context

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.classinfo.ClassDetail
import no.f12.codenavigator.navigation.interfaces.ImplementorInfo

data class ContextResult(
    val classDetail: ClassDetail,
    val callers: List<CallTreeNode>,
    val callees: List<CallTreeNode>,
    val implementors: List<ImplementorInfo>,
    val implementedInterfaces: List<ClassName>,
)

object ContextBuilder {

    fun build(
        classDetail: ClassDetail,
        callers: List<CallTreeNode>,
        callees: List<CallTreeNode>,
        implementors: List<ImplementorInfo>,
        implementedInterfaces: List<ClassName>,
    ): ContextResult = ContextResult(
        classDetail = classDetail,
        callers = callers,
        callees = callees,
        implementors = implementors,
        implementedInterfaces = implementedInterfaces,
    )
}
