package me.hellrevenger.mapperplugin

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression


class MapperFoldBuilder : FoldingBuilderEx() {

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean
    ): Array<out FoldingDescriptor?> {

        val descriptors = mutableListOf<FoldingDescriptor>()
        val types = mutableSetOf<Class<*>>()
        val types2 = mutableSetOf<Class<*>>()

        val mapperUtil = MapperUtil.of(root.project)

        root.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                element.acceptChildren(this)



                (element.parent as? KtQualifiedExpression)?.let { parent ->
                    val isSafe = parent is KtSafeQualifiedExpression
                    if(parent !is KtDotQualifiedExpression && !isSafe) {
                        return@let
                    }


                    var mappingKey = "${(parent.firstChild as? KtExpression)?.getKaTypeAsString()}."
                    if(isSafe) {
                        mappingKey = mappingKey.replace("?", "")
                    }
                    val result = if(parent.lastChild is KtCallExpression) {
                        val member = parent.lastChild.firstChild
                        mapperUtil.mapping.methods["${member.text}"] to member
                    } else {
                        val member = parent.lastChild
                        mapperUtil.mapping.fields["${member.text}"] to member
                    }
                    if(result.first != null) {
                        descriptors.add(FoldingDescriptor(
                            result.second.node, result.second.textRange,
                            null, result.first!!
                        ))
                    } else if(parent.firstChild.text.contains("FClient")) {
                        val a = element.text
                        val b = parent.text
//                        println("null ${parent.firstChild.text}")
                    }


                    val ref = parent.firstChild.reference?.resolve()

                    if(ref != null) {

//                            println("visiting ${parent.text}")
//                            println("${element.getKaType()}.${it.lastChild.text}")
//                            if(element.getKaType().toString() == "java/io/File") {
//                                if(parent.lastChild is KtCallExpression) {
//                                    descriptors.add(FoldingDescriptor(parent.lastChild.firstChild, parent.lastChild.firstChild.textRange))
//                                } else {
//                                    descriptors.add(FoldingDescriptor(parent.lastChild, parent.lastChild.textRange))
//                                }
//                            }

                    } else {
//                            println("null ref ${parent.firstChild.text}")
//                            if(parent.firstChild.text.contains("minecraft")) {
//                                println()
//                            }
                    }

                }
            }
        })

        return descriptors.toTypedArray()
    }

    override fun isCollapsedByDefault(p0: ASTNode) = true

    override fun getPlaceholderText(p0: ASTNode): String? {
        return "mapped${p0.text}"
    }
}