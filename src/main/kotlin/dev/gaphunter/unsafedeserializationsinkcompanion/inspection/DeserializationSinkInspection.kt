package dev.gaphunter.unsafedeserializationsinkcompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import dev.gaphunter.unsafedeserializationsinkcompanion.detect.JavaDeserializationSinkFinder
import dev.gaphunter.unsafedeserializationsinkcompanion.model.DeserializationSinkHit
import dev.gaphunter.unsafedeserializationsinkcompanion.review.ReviewPrompt

/**
 * Flags a `new ObjectInputStream(...).readObject()` call inside a
 * Spring MVC/JAX-RS endpoint method whose constructor argument traces
 * back (within the same method, one direct hop) to an untrusted
 * parameter -- CWE-502, Deserialization of Untrusted Data, one of the
 * most-cited OWASP Top 10 categories for Java, a real path to remote
 * code execution via gadget chains.
 *
 * Runs via `checkFile` (same shape as every other inspection in this
 * catalog); [JavaDeserializationSinkFinder] does the real PSI walk.
 */
class DeserializationSinkInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.text.length > MAX_FILE_LENGTH) return null
        if (file !is PsiJavaFile) return null

        val hits = JavaDeserializationSinkFinder.findAll(file)
        if (hits.isEmpty()) return null

        val problems = hits.map { hit ->
            manager.createProblemDescriptor(
                hit.anchor,
                messageFor(hit),
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        val path = file.virtualFile?.path
        if (path != null) {
            for (hit in hits) {
                val lineNumber = file.viewProvider.document?.getLineNumber(hit.anchor.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber")
            }
        }

        return problems.toTypedArray()
    }

    private fun messageFor(hit: DeserializationSinkHit): String =
        "ObjectInputStream deserializes '${hit.taintedSourceName}', an untrusted controller parameter -- " +
            "a crafted payload can achieve remote code execution via a gadget chain (CWE-502, Deserialization of Untrusted Data)"
}
