package sle.editor.project.assets

import androidx.compose.runtime.*
import sle.editor.project.relativeProjectPath

internal class AssetBrowserState {
    var snapshot by mutableStateOf<AssetSnapshot?>(null)
        private set
    var folder by mutableStateOf("")
        private set
    var selected by mutableStateOf<String?>(null)
        private set
    var expanded by mutableStateOf(setOf(""))
        private set
    var refreshing by mutableStateOf(true)
        private set
    var requestRefresh: ()->Unit = {}
    val directories get()=snapshot?.directories.orEmpty()
    val children get()=snapshot?.children?.get(folder).orEmpty()
    val selectedEntry get()=snapshot?.byPath?.get(selected)
    fun accept(value: AssetSnapshot) {
        snapshot=value;refreshing=false
        val folders=value.folders
        while(folder !in folders) folder=folder.substringBeforeLast('/',"")
        if(selected !in value.byPath) selected=null
        expanded=expanded.intersect(folders)+""
    }
    fun navigate(path: String) {
        if(path.isNotEmpty()) relativeProjectPath(path)
        if(path.isNotEmpty() && snapshot?.folders?.contains(path)!=true) return
        folder=path;selected=null
        var current=path
        val ancestors=mutableSetOf("")
        while(current.isNotEmpty()) { ancestors.add(current);current=current.substringBeforeLast('/',"") }
        expanded=expanded+ancestors
    }
    fun select(path: String) { if(snapshot?.byPath?.containsKey(path)==true) selected=path }
    fun toggle(path: String) { expanded=if(path in expanded) expanded-path else expanded+path }
    fun refresh() { refreshing=true;requestRefresh() }
}
