package me.hellrevenger.mapperplugin

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.impl.search.JavaFilesSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import net.fabricmc.mappingio.FlatMappingVisitor
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingFlag
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.MappingWriter
import net.fabricmc.mappingio.adapter.FlatAsRegularMappingVisitor
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import org.jetbrains.kotlin.idea.refactoring.memberInfo.qualifiedClassNameForRendering

class MapperUtil private constructor(val project: Project) {
    companion object {
        val instances = mutableMapOf<Project, MapperUtil>()

        fun of(project: Project): MapperUtil {
            val instance = instances.get(project)
            if (instance == null) {
                val instance = MapperUtil(project)
                instances[project] = instance
                return instance
            }
            return instance
        }
    }
    val file = project.projectFile!!.parent.toNioPath().resolve("mappings.tiny").toFile()

    val mapping = MappingData()

    init {
        loadMapping()
    }

    fun loadMapping() {
        if(!file.exists()) {
            println("mapping not found")
            println(file.absolutePath)
            return
        }
        MappingReader.read(
            file.reader(),
            FlatAsRegularMappingVisitor(MyVisitor(mapping))
        )
        AllClassesSearch.search(JavaFilesSearchScope(project), project).forEach { clazz ->
            val name = clazz.qualifiedName?.replace(".", "/") ?: return@forEach
            if (mapping.classes.contains(name)) {
                mapping.psiClasses[name] = clazz
            }
        }
    }

    val pattern = "(\\w+([./]\\w+)+)".toPattern()

    fun mapTypes(sign: String): String {
        return pattern.matcher(sign).replaceAll {
            val slash = it.group().slash()
            mapping.classes[slash] ?: slash
        }
    }

    fun mapTypesSimple(sign: String): String {
        return pattern.matcher(sign).replaceAll {
            val slash = it.group().slash()
            val mapped = mapping.classes[slash] ?: slash
            mapped.simple()
        }
    }
}

class MappingData {
    val fields = mutableMapOf<String, String>()
    val methods = mutableMapOf<String, String>()
    val fullFields = mutableMapOf<String, String>()
    val fullMethods = mutableMapOf<String, String>()
    val classes = mutableMapOf<String, String>()
    val psiClasses = mutableMapOf<String, PsiClass>()
}

class MyVisitor(val mapping: MappingData) : FlatMappingVisitor {
    fun anyNullOrEquals(a: Any?, b: Any?): Boolean {
        return a == null || b == null || a == b
    }

    override fun visitClass(srcName: String, dstNames: Array<out String?>): Boolean {
        if(!anyNullOrEquals(srcName, dstNames[0])) {
            mapping.classes[srcName] = dstNames[0]!!
        }
        return true
    }

    override fun visitField(
        srcClsName: String?,
        srcName: String?,
        srcDesc: String?,
        dstClsNames: Array<out String?>?,
        dstNames: Array<out String?>,
        dstDescs: Array<out String?>?
    ): Boolean {
        if(!anyNullOrEquals(srcName, dstNames[0])) {
            mapping.fullFields["$srcClsName.$srcName"] = dstNames[0]!!
            mapping.fields["$srcName"] = dstNames[0]!!
        }
        return false
    }

    override fun visitMethod(
        srcClsName: String?,
        srcName: String?,
        srcDesc: String?,
        dstClsNames: Array<out String?>?,
        dstNames: Array<out String?>,
        dstDescs: Array<out String?>?
    ): Boolean {
        if(!anyNullOrEquals(srcName, dstNames[0])) {
            mapping.methods["$srcName"] = dstNames[0]!!
            mapping.fullMethods["$srcClsName.$srcName"] = dstNames[0]!!
        }
        return false
    }

    override fun visitMethodArg(
        srcClsName: String?,
        srcMethodName: String?,
        srcMethodDesc: String?,
        argPosition: Int,
        lvIndex: Int,
        srcName: String?,
        dstClsNames: Array<out String?>?,
        dstMethodNames: Array<out String?>?,
        dstMethodDescs: Array<out String?>?,
        dstNames: Array<out String?>
    ): Boolean {
        return false
    }



    override fun visitNamespaces(
        srcNamespace: String?,
        dstNamespaces: List<String?>?
    ) { }

    override fun visitClassComment(
        srcName: String?,
        dstNames: Array<out String?>?,
        comment: String?
    ) { }

    override fun visitFieldComment(
        srcClsName: String?,
        srcName: String?,
        srcDesc: String?,
        dstClsNames: Array<out String?>?,
        dstNames: Array<out String?>?,
        dstDescs: Array<out String?>?,
        comment: String?
    ) { }
    override fun visitMethodComment(
        srcClsName: String?,
        srcName: String?,
        srcDesc: String?,
        dstClsNames: Array<out String?>?,
        dstNames: Array<out String?>?,
        dstDescs: Array<out String?>?,
        comment: String?
    ) { }

    override fun visitMethodArgComment(
        srcClsName: String?,
        srcMethodName: String?,
        srcMethodDesc: String?,
        argPosition: Int,
        lvIndex: Int,
        srcName: String?,
        dstClsNames: Array<out String?>?,
        dstMethodNames: Array<out String?>?,
        dstMethodDescs: Array<out String?>?,
        dstNames: Array<out String?>?,
        comment: String?
    ) { }

    override fun visitMethodVar(
        srcClsName: String?,
        srcMethodName: String?,
        srcMethodDesc: String?,
        lvtRowIndex: Int,
        lvIndex: Int,
        startOpIdx: Int,
        endOpIdx: Int,
        srcName: String?,
        dstClsNames: Array<out String?>?,
        dstMethodNames: Array<out String?>?,
        dstMethodDescs: Array<out String?>?,
        dstNames: Array<out String?>?
    ): Boolean { return false }

    override fun visitMethodVarComment(
        srcClsName: String?,
        srcMethodName: String?,
        srcMethodDesc: String?,
        lvtRowIndex: Int,
        lvIndex: Int,
        startOpIdx: Int,
        endOpIdx: Int,
        srcName: String?,
        dstClsNames: Array<out String?>?,
        dstMethodNames: Array<out String?>?,
        dstMethodDescs: Array<out String?>?,
        dstNames: Array<out String?>?,
        comment: String?
    ) { }
}