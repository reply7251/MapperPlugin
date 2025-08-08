package me.hellrevenger.mapperplugin

import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaType


@RequiresReadLock
fun KtExpression.getKaType(): KaType? {
    try {
        analyze(this) {
            if(expressionType.toString().slash() == "kotlin/Unit")
                return null
            return expressionType
        }
    } catch (e: Exception) {
        return null
    }
}

@RequiresReadLock
fun KtExpression.getKaTypeAsString(): String? {
    return (this.getKaType() ?:
        this.reference()?.resolve()?.kotlinFqName?.let {
            return@let "L$it"
        }
    )?.toString()?.split(" ")?.last()?.replace("!", "")?.slash()
}

fun String.dot() = this.replace("/", ".").replace("$", ".")

fun String.slash() = this.replace(".", "/").replace("$", "/")

fun String.simple() = this.slash().split("/").last()