package me.hellrevenger.mapperplugin

import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtExpression


@RequiresReadLock
fun KtExpression.getKaType(): ClassifierDescriptor? {
    try {
        val bindingContext = this.analyze()
        val result = bindingContext.getType(this)
        if(result != null) {
            return result.constructor.declarationDescriptor
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

fun String.dot() = this.replace("/", ".")

fun String.slash() = this.replace(".", "/")

fun String.simple() = this.split("/").last()