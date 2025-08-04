package me.hellrevenger.mapperplugin

import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression


@RequiresReadLock
fun KtExpression.getKaType(): String? {
    try {
        val bindingContext = this.analyze()
        val result = bindingContext.getType(this)
        if(result != null) {
            return result.constructor.declarationDescriptor.toString()
        } else {
            var name: String? = null
            (this as? KtElement)?.let {
                it.reference()?.resolve()?.kotlinFqName?.let {
                    return "L$it"
                }
            }
            this.reference?.resolve()?.kotlinFqName?.let {
                return "L$it"
            }
        }
        return null
    } catch (e: Exception) {
        return null
    }
}

@RequiresReadLock
fun KtExpression.getKaTypeAsString(): String? {
    return this.getKaType()?.toString()?.split(" ")?.last()?.replace("!", "")?.slash()
}

fun String.dot() = this.replace("/", ".").replace("$", ".")

fun String.slash() = this.replace(".", "/").replace("$", "/")

fun String.simple() = this.slash().split("/").last()