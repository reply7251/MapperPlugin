package me.hellrevenger.mapperplugin

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.PlatformIcons
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.j2k.accessModifier
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.resolve.ImportPath

class MapperContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(KotlinLanguage.INSTANCE),
            MyProvider("BASIC")
        )
    }
}

class MyProvider(val id: String) : CompletionProvider<CompletionParameters>() {
    val cachedClasses = mutableMapOf<Project, List<LookupElement>>()

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        results: CompletionResultSet
    ) {
        val project = parameters.position.project
        val mapper = MapperUtil.of(project)

        if(parameters.position.parent.parent !is KtQualifiedExpression) {
            (parameters.position as? LeafPsiElement)?.let { element ->
                val cache = cachedClasses[project]
                if(cache.isNullOrEmpty()) {
                    val list = mutableListOf<LookupElement>()
                    mapper.mapping.classes.forEach { (k, v) ->
                        val clazz = mapper.mapping.psiClasses[k] ?: return@forEach
                        list.add(wrapLookup(
                            classLookup(
                                clazz,
                                v.simple(),
                                " (${v.dot()})"
                            ), k.simple()) { insertionContext ->
                            val doc = insertionContext.document
                            (insertionContext.file as? KtFile)?.let { file ->
                                if(!file.importDirectives.any {
                                    it.importedFqName?.asString() == k.dot()
                                }) {
                                    val path = ImportPath(FqName(k.dot()), false, null)
                                    val importDirective = KtPsiFactory(project).createImportDirective(path)
                                    val elem = file.importDirectives.lastOrNull() ?: file.packageDirective

                                    elem?.let {
                                        doc.insertString(elem.endOffset, "\n"+ importDirective.text)
                                    }
                                }

                            }
                        })
                    }
                    results.addAllElements(list)
                    cachedClasses[project] = list
                } else {
                    results.addAllElements(cache)
                }
            }
        }
        (parameters.position.parent?.parent?.firstChild as? KtExpression)?.let {
            var type = it.getKaTypeAsString()?.replace("?", "") ?: return@let
            val isStatic = type.startsWith("L")
            if(isStatic) {
                type = type.substring(1)
            }

            fun suggestForType(type: String, modifiers: List<String> = listOf("public")) {
                mapper.mapping.psiClasses[type]?.let { clazz ->
                    val className = mapper.mapping.classes[type]!!

                    clazz.allMethods.forEach { method ->
                        val dstName = mapper.mapping.methods[method.name] ?: return@forEach

                        if(method.hasModifier(JvmModifier.STATIC) == isStatic) {
                            val (suggestName, actualName) = if(modifiers.any { method.accessModifier().contains(it) }) {
                                dstName to "${method.name}()"
                            } else {
                                "_$dstName" to "_invokePrivate(\"${method.name}\", arrayOf())"
                            }
                            results.addElement(wrapLookup(
                                methodLookup(
                                    method,
                                    suggestName,
                                    mapper.mapTypesSimple("${method.parameterList.text}: ${method.returnType?.canonicalText}"),
                                    className.simple()
                                ), actualName
                            ))
                        }
                    }

                    clazz.allFields.forEach { field ->
                        val dstName = mapper.mapping.fields[field.name] ?: return@forEach

                        if(field.hasModifier(JvmModifier.STATIC) == isStatic) {
                            val (suggestName, actualName) = if(modifiers.any { field.accessModifier().contains(it) }) {
                                dstName to field.name
                            } else {
                                "_$dstName" to "_getField(\"${field.name}\")"
                            }
                            results.addElement(wrapLookup(
                                fieldLookup(
                                    field,
                                    suggestName,
                                    ": ${mapper.mapTypesSimple(field.type.canonicalText)}",
                                    className.simple()
                                ),actualName
                            ))
                        }
                    }
                }
            }
            suggestForType(type)
            if(it is KtThisExpression) {
                analyze(it) {
                    val type = it.expressionType ?: return@analyze
                    type.directSupertypes.forEach {
                        suggestForType(it.toString().split(" ").last().replace("!", "").slash(), listOf("public", "protected"))
                    }
                }
            }
        }
        results.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create("The Following are generated"), 10.0))
    }

    fun wrapLookup(lookupElement: LookupElement, actualName: String, insertCallback: (InsertionContext) -> Unit = {}): LookupElement {
        return LookupElementDecorator.withInsertHandler<LookupElement>(
            lookupElement, MapperInsertHandler(actualName, insertCallback)
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

    fun classLookup(element: PsiClass, renderName: String, tail: String): LookupElement {
        return LookupElementBuilder.createWithSmartPointer(
            renderName, element
        ).withTailText(tail, true)
            .withIcon(PlatformIcons.CLASS_ICON)
    }
}

class MapperInsertHandler(val targetText: String, val callback: (InsertionContext) -> Unit = {}) : InsertHandler<LookupElement> {
    companion object {
        val placeHolderPattern = "\\w+".toRegex()
    }

    override fun handleInsert(
        context: InsertionContext,
        lookup: LookupElement
    ) {
        context.document.replaceString(context.startOffset, context.tailOffset, targetText)
        callback(context)
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