package dev.gaphunter.unsafedeserializationsinkcompanion.model

import com.intellij.psi.PsiElement

/** One `new ObjectInputStream(...)` construction whose argument traces back to an untrusted controller-method source, later consumed via `.readObject()`. */
data class DeserializationSinkHit(
    val anchor: PsiElement,
    val taintedSourceName: String,
)
