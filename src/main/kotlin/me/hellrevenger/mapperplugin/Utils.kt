package me.hellrevenger.mapperplugin

import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtExpression


@RequiresReadLock
fun KtExpression.getKaType(): KaType? {
    try {
        analyze(this) {
            return expressionType
        }
    } catch (e: Exception) {
        return null
    }
}

@RequiresReadLock
fun KtExpression.getKaTypeAsString(): String? {
    return this.getKaType()?.toString()?.split(" ")?.last()?.replace("!", "")
}

fun String.dot() = this.replace("/", ".")

fun String.slash() = this.replace(".", "/")

fun String.simple() = this.split("/").last()