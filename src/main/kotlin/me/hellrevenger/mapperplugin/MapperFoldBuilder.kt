package me.hellrevenger.mapperplugin

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression


class MapperFoldBuilder : FoldingBuilderEx() {

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean
    ): Array<out FoldingDescriptor?> {

        val descriptors = mutableListOf<FoldingDescriptor>()

        val mapperUtil = MapperUtil.of(root.project)

        root.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                element.acceptChildren(this)

                (element as? KtReferenceExpression)?.let {
                    if(element is KtCallExpression) return@let
                    var type = it.getKaTypeAsString() ?: return@let
                    if(!type.startsWith("L")) {
                        return@let
                    }
                    type = type.substring(1)
                    val sub = type.substringBeforeLast("/")
                    if(sub.endsWith(type.substring(sub.length))) {
                        type = sub
                    }
                    mapperUtil.mapping.classes[type]?.let { _newName ->
                        val newName = if(element.parent.parent is KtImportDirective) {
                            _newName.dot().substring(element.parent.firstChild.text.length+1)
                        } else {
                            _newName.simple()
                        }
                        descriptors.add(FoldingDescriptor(
                            element.node, element.textRange,
                            null, newName
                        ))
                    }
                }

                (element.parent as? KtQualifiedExpression)?.let { parent ->
                    val isSafe = parent is KtSafeQualifiedExpression
                    if(parent !is KtDotQualifiedExpression && !isSafe) {
                        return@let
                    }

                    val result = if(parent.lastChild is KtCallExpression) {
                        val member = parent.lastChild.firstChild
                        mapperUtil.mapping.methods[member.text] to member
                    } else {
                        val member = parent.lastChild
                        mapperUtil.mapping.fields[member.text] to member
                    }
                    if(result.first != null) {
                        descriptors.add(FoldingDescriptor(
                            result.second.node, result.second.textRange,
                            null, result.first!!
                        ))
                    }
                }

                (element as? KtLiteralStringTemplateEntry)?.let {
                    val from = it.text
                    val mapped = if(from.startsWith("method_") || from.startsWith("m_")) {
                        mapperUtil.mapping.methods[from]
                    } else if (from.startsWith("field_") || from.startsWith("f_")) {
                        mapperUtil.mapping.fields[from]
                    } else if (from.contains("class_") || from.contains("C_")) {
                        mapperUtil.mapping.classes[from.slash()]
                    } else {
                        null
                    }

                    mapped?.let { dstName ->
                        descriptors.add(FoldingDescriptor(
                            element.node, element.textRange,
                            null, dstName
                        ))
                    }
                }

                (element as? LeafPsiElement)?.let { identifier ->
                    (identifier.parent as? KtNamedFunction)?.let { parent ->
                        if(!parent.hasModifier(KtTokens.OVERRIDE_KEYWORD))
                            return@let
                        val from = identifier.text
                        if(from.startsWith("method_") || from.startsWith("m_")) {
                            val mapped = mapperUtil.mapping.methods[identifier.text]
                            mapped?.let {  dstName ->
                                descriptors.add(FoldingDescriptor(
                                    element.node, element.textRange,
                                    null, dstName
                                ))
                            }
                        }

                    }
                }
            }
        })

        return descriptors.toTypedArray()
    }

    override fun isCollapsedByDefault(p0: ASTNode) = true

    override fun getPlaceholderText(p0: ASTNode): String {
        return "mapped${p0.text}"
    }
}