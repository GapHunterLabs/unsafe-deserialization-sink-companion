package dev.gaphunter.unsafedeserializationsinkcompanion.detect

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import dev.gaphunter.unsafedeserializationsinkcompanion.model.DeserializationSinkHit

/**
 * Follows the real data flow (bounded to the SAME method, one direct
 * hop to a parameter -- never a full project-wide taint analysis)
 * from an untrusted source (a parameter of a Spring MVC/JAX-RS
 * endpoint method, see [ControllerEndpointSignals]) to a
 * `new ObjectInputStream(...).readObject()` call that consumes it --
 * CWE-502, Deserialization of Untrusted Data.
 *
 * **v0.1 scope, stated honestly:** only follows the data within the
 * same method and a direct one-hop reference to a parameter -- never
 * crosses more than one method of distance, and never resolves a
 * value assigned across multiple intermediate variables. The
 * untrusted source is defined by a closed list of known controller
 * signatures ([ControllerEndpointSignals]), not a general taint
 * analysis of the whole project.
 */
object JavaDeserializationSinkFinder {

    fun findAll(file: PsiFile): List<DeserializationSinkHit> {
        val hits = mutableListOf<DeserializationSinkHit>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                if (!ControllerEndpointSignals.isEndpointMethod(method)) return
                hits += hitsForMethod(method)
            }
        })
        return hits
    }

    private fun hitsForMethod(method: PsiMethod): List<DeserializationSinkHit> {
        val body = method.body ?: return emptyList()
        val taintedNames = method.parameterList.parameters.map { it.name }.toSet()
        if (taintedNames.isEmpty()) return emptyList()

        val hits = mutableListOf<DeserializationSinkHit>()
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitNewExpression(expression: PsiNewExpression) {
                super.visitNewExpression(expression)
                val classRef = expression.classReference ?: return
                if (classRef.referenceName != "ObjectInputStream") return

                val constructorArg = expression.argumentList?.expressions?.getOrNull(0) ?: return
                val taintedName = firstTaintedReference(constructorArg, taintedNames) ?: return

                if (isEventuallyDeserialized(expression, body)) {
                    hits += DeserializationSinkHit(anchorOf(expression), taintedName)
                }
            }
        })
        return hits
    }

    /** The first tainted parameter name referenced anywhere inside [expression]'s own subtree (a direct reference, or one hop through a wrapping call like `new ByteArrayInputStream(param)`). */
    private fun firstTaintedReference(expression: PsiElement, taintedNames: Set<String>): String? {
        var found: String? = null
        expression.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitReferenceExpression(expr: PsiReferenceExpression) {
                if (found != null) return
                super.visitReferenceExpression(expr)
                val name = expr.referenceName
                if (name != null && name in taintedNames) found = name
            }
        })
        return found
    }

    /**
     * True when [newExpression]'s constructed `ObjectInputStream` is
     * actually consumed via `.readObject()` -- either chained directly
     * (`new ObjectInputStream(x).readObject()`) or assigned to a local
     * variable that has `.readObject()` called on it somewhere else in
     * [methodBody]. A construction that's never actually read from is
     * dead code, not a real sink.
     */
    private fun isEventuallyDeserialized(newExpression: PsiNewExpression, methodBody: PsiElement): Boolean {
        // For `new ObjectInputStream(x).readObject()`, newExpression's
        // direct parent is the METHOD EXPRESSION `....readObject`
        // itself (a PsiReferenceExpression, "x" in Java PSI's own
        // `a.b()` shape), not the enclosing PsiMethodCallExpression --
        // confirmed the hard way: checking for a PsiMethodCallExpression
        // parent directly never matched, silently missing every
        // directly-chained call.
        val chainedReference = newExpression.parent as? PsiReferenceExpression
        if (chainedReference != null && chainedReference.referenceName == "readObject") return true

        val localVariable = newExpression.parent as? PsiLocalVariable ?: return false
        val varName = localVariable.name

        var found = false
        methodBody.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                if (found) return
                super.visitMethodCallExpression(call)
                if (call.methodExpression.referenceName != "readObject") return
                val qualifier = call.methodExpression.qualifierExpression as? PsiReferenceExpression ?: return
                if (qualifier.referenceName == varName) found = true
            }
        })
        return found
    }

    private fun anchorOf(element: PsiElement): PsiElement {
        var current = element
        while (current.firstChild != null) current = current.firstChild
        return current
    }
}
