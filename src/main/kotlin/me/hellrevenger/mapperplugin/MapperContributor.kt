package me.hellrevenger.mapperplugin

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.folding.impl.FoldingUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.ide.DataManager
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.event.MockDocumentEvent
import com.intellij.openapi.editor.impl.CaretImpl
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.search.JavaFilesSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.ProcessingContext
import com.intellij.util.concurrency.EdtScheduler
import com.jetbrains.rd.framework.base.deepClonePolymorphic
import me.hellrevenger.mapperplugin.getKaType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.completion.smart.withOptions
import org.jetbrains.kotlin.idea.core.ItemOptions
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.convertToJvmType
import org.jetbrains.kotlin.j2k.accessModifier
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

class MapperContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(KotlinLanguage.INSTANCE),
            MyProvider("BASIC")
        )
//        extend(
//            CompletionType.SMART,
//            PlatformPatterns.psiElement(),
//            MyProvider("BASIC")
//        )
    }
}

class MyProvider(val id: String) : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        results: CompletionResultSet
    ) {
        //println("$id suggesting: ${parameters.position::class.java}")

        (parameters.position.parent?.parent?.firstChild as? KtExpression)?.let {
            val type = it.getKaTypeAsString()?.replace("?", "") ?: return@let

            val project = parameters.position.project
            val mapper = MapperUtil.of(project)

            mapper.mapping.psiClasses[type]?.let { clazz ->
                var counter = 0
                val className = mapper.mapping.classes[type]!!
                clazz.allMethods.forEach { method ->
                    val dstName = mapper.mapping.methods[method.name] ?: return@forEach

                    if(method.accessModifier().contains("public")) {
                        counter++
                        results.addElement(wrapLookup(
                            methodLookup(
                                method,
                                dstName,
                                mapper.mapTypesSimple("${method.parameterList.text}: ${method.returnType?.canonicalText}"),
                                className.simple()
                            ),"${method.name}()", counter
                        ))
                    }
                }

                clazz.allFields.forEach { field ->
                    val dstName = mapper.mapping.fields[field.name] ?: return@forEach

                    if(field.accessModifier().contains("public")) {
                        counter++
                        results.addElement(wrapLookup(
                            fieldLookup(
                                field,
                                dstName,
                                ": ${mapper.mapTypesSimple(field.type.canonicalText)}",
                                className.simple()
                            ),field.name, counter * 10
                        ))
                    }
                }
            }
        }
        results.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create("Hello"), 3000.0))
    }

    fun wrapLookup(lookupElement: LookupElement, actualName: String, counter: Int): LookupElement {
        return LookupElementDecorator.withInsertHandler<LookupElement>(
            PrioritizedLookupElement.withPriority(
                lookupElement, 1000000.0 - counter
            ), MapperInsertHandler(actualName)
        )
    }

    fun methodLookup(element: PsiMethod, renderName: String, tail: String, type: String): LookupElement {
        return LookupElementBuilder.createWithSmartPointer(
            renderName, element
        ).withTailText(tail, true).withTypeText(type, true)
            .withIcon(PlatformIcons.FUNCTION_ICON)
    }

    fun fieldLookup(element: PsiField, renderName: String, tail: String, type: String): LookupElement {
        return LookupElementBuilder.createWithSmartPointer(
            renderName, element
        ).withTailText(tail, true).withTypeText(type, true)
            .withIcon(PlatformIcons.FIELD_ICON)
    }
}

class MapperInsertHandler(val targetText: String) : InsertHandler<LookupElement> {
    companion object {
        val placeHolderPattern = "\\w+".toRegex()
    }

    override fun handleInsert(
        context: InsertionContext,
        lookup: LookupElement
    ) {
        context.document.replaceString(context.startOffset, context.tailOffset, targetText)

        (context.editor.foldingModel as? FoldingModelImpl)?.let { model ->
            model.runBatchFoldingOperation {
                model.allFoldRegions.forEach {
                    if(placeHolderPattern.matches(it.placeholderText)) {
                        it.isExpanded = false
                    }
                }
            }
        }
    }
}