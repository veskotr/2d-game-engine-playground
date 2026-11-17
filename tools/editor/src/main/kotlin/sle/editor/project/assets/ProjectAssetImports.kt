package sle.editor.project.assets

import java.nio.file.*
import java.util.UUID

internal enum class ImportCollision { Fail, Rename, Skip, Replace }
internal data class AssetImportResult(val path: Path?,val reused: Boolean=false)

/** Filesystem import only: never deletes sources or infers script dependencies. */
internal class ProjectAssetImports(private val project: Path,private val assets: Path) {
    fun importAsset(source: Path,folder: String,collision: ImportCollision=ImportCollision.Fail): AssetImportResult = synchronized(AssetIdentity) {
        val root=assetPath(project,assets)
        val destination=assetPath(project,assets,folder)
        require(Files.isDirectory(destination,LinkOption.NOFOLLOW_LINKS)) { "Import folder no longer exists" }
        val input=source.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(input) && Files.exists(input,LinkOption.NOFOLLOW_LINKS)) { "Import source is missing or a link" }
        val real=input.toRealPath()
        require(real==input.toRealPath(LinkOption.NOFOLLOW_LINKS)) { "Import source points through a link" }
        require(!AssetIdentity.isMetadata(real)) { "Import the asset, not its .slemeta file" }
        if(real.startsWith(root)) {
            val relative=root.relativize(real).toString().replace('\\','/')
            assetPath(project,assets,relative)
            val snapshot=AssetScanner.scan(project,assets)
            require(snapshot.errors.isEmpty()) { snapshot.errors.joinToString("\n") }
            return AssetImportResult(real,true)
        }
        require(!root.startsWith(real)) { "Cannot import a folder containing this project" }
        var target=destination.resolve(real.fileName.toString())
        require(!AssetIdentity.isMetadata(target)) { "Reserved metadata suffix" }
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) when(collision) {
            ImportCollision.Fail -> errorCollision(target)
            ImportCollision.Skip -> return AssetImportResult(null)
            ImportCollision.Rename -> {
                val name=real.fileName.toString();val directory=Files.isDirectory(real)
                val dot=if(directory) -1 else name.lastIndexOf('.').takeIf { it>0 } ?: -1
                val stem=if(dot<0) name else name.substring(0,dot);val extension=if(dot<0) "" else name.substring(dot)
                var index=1
                do { target=destination.resolve("$stem ($index)$extension");index++ }
                while(Files.exists(target,LinkOption.NOFOLLOW_LINKS) || Files.exists(AssetIdentity.sidecar(target),LinkOption.NOFOLLOW_LINKS))
            }
            ImportCollision.Replace -> Unit
        }
        val sources=if(Files.isDirectory(real,LinkOption.NOFOLLOW_LINKS)) Files.walk(real).use { it.toList() } else listOf(real)
        data class Copy(val source: Path,val target: Path,val directory: Boolean,val id: String?)
        val snapshot=AssetScanner.scan(project,assets)
        require(snapshot.errors.isEmpty()) { snapshot.errors.joinToString("\n") }
        val incoming=mutableSetOf<String>()
        val copies=sources.filterNot { AssetIdentity.isMetadata(it) }.map { file ->
            require(!Files.isSymbolicLink(file) && file.toRealPath()==file.toRealPath(LinkOption.NOFOLLOW_LINKS)) { "Import contains a link: $file" }
            val out=if(file==real) target else target.resolve(real.relativize(file).toString())
            assetPath(project,assets,root.relativize(out).toString().replace('\\','/'))
            val dir=Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS)
            require(dir || Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) { "Unsupported import source: $file" }
            val exists=Files.exists(out,LinkOption.NOFOLLOW_LINKS)
            if(exists) require(collision==ImportCollision.Replace && dir==Files.isDirectory(out,LinkOption.NOFOLLOW_LINKS)) { "Cannot replace a file with a folder or a folder with a file: $out" }
            val meta=AssetIdentity.sidecar(file)
            val sourceId=if(!dir && Files.exists(meta,LinkOption.NOFOLLOW_LINKS)) AssetIdentity.read(meta) else null
            if(sourceId!=null) {
                require(incoming.add(sourceId)) { "Duplicate asset UUID in import: $sourceId" }
                val existing=snapshot.pathsById[sourceId]
                require(existing==null || root.resolve(existing)==out) { "Duplicate asset UUID $sourceId already belongs to $existing" }
            }
            val id=if(dir) null else if(exists) snapshot.idsByPath.getValue(root.relativize(out).toString().replace('\\','/')) else sourceId ?: UUID.randomUUID().toString()
            if(!dir && !exists) require(!Files.exists(AssetIdentity.sidecar(out),LinkOption.NOFOLLOW_LINKS)) { "Orphan metadata blocks import: $out" }
            Copy(file,out,dir,id)
        }
        // All metadata, identity and path checks precede writes. Each file replacement
        // is atomic; folder replacement merges and retains unmentioned destination files.
        for(copy in copies) {
            assetPath(project,assets,root.relativize(copy.target).toString().replace('\\','/'))
            if(copy.directory) { if(!Files.exists(copy.target)) Files.createDirectory(copy.target) }
            else {
                val exists=Files.exists(copy.target,LinkOption.NOFOLLOW_LINKS)
                if(!exists) atomicAssetWrite(AssetIdentity.sidecar(copy.target),AssetIdentity.encode(copy.id!!).toByteArray(),false)
                try { atomicAssetCopy(copy.source,copy.target,exists && collision==ImportCollision.Replace) }
                catch(failure: Exception) {
                    if(!exists) Files.deleteIfExists(AssetIdentity.sidecar(copy.target))
                    throw failure
                }
            }
        }
        return AssetImportResult(target)
    }
    private fun errorCollision(path: Path): Nothing=throw IllegalArgumentException("Name conflict: $path. Choose Rename, Skip or Replace.")
}
