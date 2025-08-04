package me.hellrevenger.mapperplugin

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.ui.awt.RelativePoint
import net.fabricmc.mappingio.FlatMappingVisitor
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.FlatAsRegularMappingVisitor

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

        AllClassesSearch.search(MySearchScope(project), project).forEach { clazz ->
            val name = clazz.qualifiedName?.slash() ?: return@forEach
            if (mapping.classes.contains(name)) {
                mapping.psiClasses[name] = clazz
            }
        }

        val statusBar = WindowManager.getInstance().getStatusBar(project).component ?: return
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder("mapping processed", MessageType.INFO, null)
            .setFadeoutTime(5000)
            .createBalloon()
            .show(RelativePoint.getCenterOf(statusBar), Balloon.Position.atRight)
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

class MySearchScope(private val project: Project) : GlobalSearchScope(project) {
    val psiManager = PsiManager.getInstance(project)

    override fun contains(vf: VirtualFile): Boolean {
        if(vf.isDirectory) return false
        val provider = psiManager.findViewProvider(vf) ?: return false
        if(provider.hasLanguage(JavaLanguage.INSTANCE)) {
            val flag = project.basePath?.let { vf.path.contains(it) } == true


            return flag
        }
        return false
    }

    override fun isSearchInModuleContent(p0: Module) = false

    override fun isSearchInLibraries() = true

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
            mapping.classes[srcName.slash()] = dstNames[0]!!.slash()
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